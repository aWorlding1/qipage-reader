package local.readapp.feature

import android.graphics.*
import android.text.*
import android.text.style.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import local.readapp.core.*
import java.io.*
import kotlin.math.roundToInt

internal data class TextPage(val layout:StaticLayout,val start:Long,val end:Long,val finalBottom:Int,val nextBottom:Int?,val offsets:LongArray=longArrayOf())

private const val HIGHLIGHT_COLOR=0x55C09B62
/** 章题相对正文的放大倍率（与 LineHeightSpan 里的高度计算保持同步）。 */
private const val TITLE_SCALE=1.36f
internal class TxtPaginator(val content:TextContent,val toc:List<Chapter>,val prefs:ReaderPreferences,
    val font:Typeface,val width:Int,val height:Int,val density:Float,val fontScale:Float,private val cache:File,val firstPageInset:Int=0) {
    val starts=(listOf(0L)+toc.map {it.locator.offset}).distinct().sorted()
    private val indexes=object:LinkedHashMap<Int,LongArray>(4,.75f,true){override fun removeEldestEntry(entry:MutableMap.MutableEntry<Int,LongArray>)=size>3}
    private val size=prefs.fontSize*density*fontScale
    private val gap=(prefs.paragraphSpacing*density).roundToInt()
    private val titleGap=(10*density).roundToInt()
    private val windowSize=maxOf(16384,(width/size*height/(size*prefs.lineSpacing)*4).toInt()).coerceAtMost(262144)
    fun chapter(position:Long)=starts.indexOfLast {it<=position}.coerceAtLeast(0)
    fun end(chapter:Int)=starts.getOrNull(chapter+1)?:content.length
    private fun styled(data:PageText,chapter:Int,color:Int,highlight:Highlight?=null):StaticLayout {
        val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {typeface=font;textSize=size;this.color=color;letterSpacing=prefs.letterSpacing}
        val text=SpannableString(data.text)
        if(highlight!=null){
            // Map the source range onto this page's text window.
            var first=-1;var last=-1
            val stop=highlight.start+highlight.length
            for(i in 0 until data.text.length){
                val offset=data.offsets[i]
                if(first<0&&offset>=highlight.start)first=i
                if(offset<stop)last=i
            }
            if(first>=0&&last>=first)text.setSpan(BackgroundColorSpan(HIGHLIGHT_COLOR),first,(last+1).coerceAtMost(text.length),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val heading=data.offsets.first()==starts[chapter] && toc.any {it.locator.offset==starts[chapter]}
        val headingEnd=if(heading)data.text.indexOf('\n').let {if(it<0)data.text.length else it} else 0
        if(heading&&headingEnd>0){
            // 章题：居中、放大、加粗——留白与居中带来"书"的仪式感。
            text.setSpan(RelativeSizeSpan(TITLE_SCALE),0,headingEnd,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(StyleSpan(Typeface.BOLD),0,headingEnd,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),0,headingEnd,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // 中文排版：正文每段首行缩进两字（章题行不缩进）。
        val indent=(size*2).roundToInt()
        var paragraphStart=0
        while(paragraphStart<data.text.length){
            if(paragraphStart>=headingEnd)text.setSpan(LeadingMarginSpan.Standard(indent,0),paragraphStart,paragraphStart+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val nextBreak=data.text.indexOf('\n',paragraphStart)
            if(nextBreak<0)break
            paragraphStart=nextBreak+1
        }
        val bodyMetrics=paint.fontMetricsInt
        val titleMetrics=TextPaint(paint).apply {textSize=size*TITLE_SCALE;typeface=Typeface.create(font,Typeface.BOLD)}.fontMetricsInt
        text.setSpan(object:LineHeightSpan {
            override fun chooseHeight(t:CharSequence,start:Int,end:Int,sv:Int,v:Int,fm:Paint.FontMetricsInt){
                val isTitle=(t as? Spanned)?.getSpans(start,end,RelativeSizeSpan::class.java)?.isNotEmpty()==true
                val metrics=if(isTitle)titleMetrics else bodyMetrics
                val lineHeight=(size*(if(isTitle)TITLE_SCALE*1.3f else prefs.lineSpacing)).roundToInt()
                val leading=lineHeight-(metrics.descent-metrics.ascent)
                // Assign from immutable metrics. Never multiply metrics already changed by a prior line.
                fm.ascent=metrics.ascent-leading/2;fm.descent=fm.ascent+lineHeight
                if(end>0&&t[end-1]=='\n')fm.descent+=gap+(if(isTitle)titleGap else 0)
                fm.top=fm.ascent;fm.bottom=fm.descent
            }
        },0,text.length,Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        fun build():StaticLayout {
            val builder=StaticLayout.Builder.obtain(text,0,text.length,paint,width.coerceAtLeast(1))
                .setIncludePad(false).setUseLineSpacingFromFallbacks(false)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            if(prefs.justify)builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
            return builder.build()
        }
        return if(prefs.justify)punctuationLayout(text,paint,width,::build) else build()
    }

    private fun trailing(layout:StaticLayout,line:Int):Int {
        val e=layout.getLineEnd(line);if(e==0||layout.text[e-1]!='\n')return 0
        val heading=(layout.text as Spanned).getSpans(layout.getLineStart(line),e,RelativeSizeSpan::class.java).isNotEmpty()
        return gap+if(heading)titleGap else 0
    }
    private fun fit(layout:StaticLayout,first:Int,available:Int=height):Int {
        val top=layout.getLineTop(first);var last=first
        while(last+1<layout.lineCount && layout.getLineStart(last+1)<layout.text.length && layout.getLineBottom(last+1)-trailing(layout,last+1)-top<=available)last++
        return last
    }
    /** 章首页要给插图让出顶部空间；其余页面用整页高度。 */
    private fun availableHeight(chapter:Int,pageStart:Long)=if(pageStart==starts[chapter])(height-firstPageInset).coerceAtLeast(1) else height
    suspend fun page(position:Long,chapter:Int,color:Int,highlight:Highlight?=null):TextPage {
        val data=textWindow(content,position,(position+windowSize).coerceAtMost(end(chapter)))
        val full=styled(data,chapter,color);val last=fit(full,0,availableHeight(chapter,position));val stop=full.getLineEnd(last).coerceAtMost(data.text.length)
        val sub=PageText(data.text.substring(0,stop),data.offsets.copyOfRange(0,stop+1))
        val layout=styled(sub,chapter,color,highlight)
        return TextPage(layout,data.offsets.first(),data.offsets[stop],full.getLineBottom(last)-trailing(full,last),if(last+1<full.lineCount&&full.getLineStart(last+1)<data.text.length)full.getLineBottom(last+1)-trailing(full,last+1) else null,sub.offsets)
    }
    private val indexMutex=Mutex()
    suspend fun index(chapter:Int):LongArray=indexMutex.withLock {buildIndex(chapter)}
    private suspend fun buildIndex(chapter:Int):LongArray {
        indexes[chapter]?.let{return it}
        val key="p7-illust-v1:${toc.any {it.locator.offset==starts[chapter]}}:${starts[chapter]}:${end(chapter)}:$width:$height:$density:$fontScale:${prefs.fontSize}:${prefs.lineSpacing}:${prefs.paragraphSpacing}:${prefs.letterSpacing}:${prefs.justify}:${prefs.fontFile}:${content.encoding}:${content.length}:$firstPageInset"
        val hash=java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString(""){"%02x".format(it)}
        val file=File(cache,"$hash.pages")
        runCatching {DataInputStream(file.inputStream().buffered()).use {input->
            val count=input.readInt();require(count in 1..2_000_000);require(file.length()==4L+count*8L)
            val values=LongArray(count){input.readLong()};require(values.first()==starts[chapter]);require(values.zipWithNextCompat().all {it.first<it.second});require(values.last()<end(chapter)||content.length==0L)
            indexes[chapter]=values;return values
        }}
        val positions=ArrayList<Long>();var position=starts[chapter]
        while(position<end(chapter)){
            currentCoroutineContext().ensureActive()
            val data=textWindow(content,position,(position+windowSize).coerceAtMost(end(chapter)))
            val layout=styled(data,chapter,Color.BLACK);var line=0;var advanced=false
            while(line<layout.lineCount && layout.getLineStart(line)<data.text.length){
                val last=fit(layout,line,availableHeight(chapter,data.offsets[layout.getLineStart(line)]));val stop=layout.getLineEnd(last).coerceAtMost(data.text.length)
                if(stop==data.text.length && data.offsets.last()<end(chapter) && line>0)break
                positions.add(data.offsets[layout.getLineStart(line)]);position=data.offsets[stop];advanced=true;line=last+1
            }
            check(advanced&&position>data.offsets.first()){T("分页未推进")}
        }
        val values=positions.ifEmpty {arrayListOf(starts[chapter])}.toLongArray()
        cache.mkdirs()
        // Bound disposable pagination caches across repeated live typography adjustments.
        cache.listFiles {f->f.extension=="pages"}?.sortedByDescending {it.lastModified()}?.drop(127)?.forEach {it.delete()}
        val temp=File.createTempFile("pages-",".tmp",cache)
        try{DataOutputStream(temp.outputStream().buffered()).use {out->out.writeInt(values.size);values.forEach(out::writeLong)};temp.renameTo(file)}finally{temp.delete()}
        indexes[chapter]=values;return values
    }
    private fun LongArray.zipWithNextCompat()=asSequence().zipWithNext()
}

internal fun pageBitmap(page:TextPage,width:Int,height:Int,margin:Int,background:Int,image:Bitmap?,dim:Float,vertical:Int,illustration:Bitmap?=null,inset:Int=0):Bitmap {
    val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);canvas.drawColor(background)
    if(image!=null){val scale=maxOf(width.toFloat()/image.width,height.toFloat()/image.height);val w=image.width*scale;val h=image.height*scale;canvas.drawBitmap(image,null,RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2),Paint(Paint.FILTER_BITMAP_FLAG));if(dim>0)canvas.drawColor(Color.argb((dim*255).toInt(),0,0,0))}
    if(illustration!=null&&inset>0){
        // 章首插图：任一张图都可既是封面又出现在正文里，居中裁切填充预留区。
        val area=RectF(margin.toFloat(),vertical.toFloat(),(width-margin).toFloat(),(vertical+inset).toFloat())
        val scale=maxOf(area.width()/illustration.width,area.height()/illustration.height)
        val dw=illustration.width*scale;val dh=illustration.height*scale
        val saved=canvas.save();canvas.clipRect(area)
        canvas.drawBitmap(illustration,null,RectF(area.centerX()-dw/2,area.centerY()-dh/2,area.centerX()+dw/2,area.centerY()+dh/2),Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restoreToCount(saved)
    }
    canvas.save();canvas.translate(margin.toFloat(),(vertical+inset).toFloat());page.layout.draw(canvas);canvas.restore();return bitmap
}
