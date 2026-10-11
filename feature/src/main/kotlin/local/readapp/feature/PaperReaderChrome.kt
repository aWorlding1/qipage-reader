package local.readapp.feature

import local.readapp.core.T

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import local.readapp.core.*

internal class ReaderChrome {
    var visible by mutableStateOf(false)
    var page by mutableIntStateOf(1)
    var count by mutableIntStateOf(1)
    var chapter by mutableStateOf("")
    var seek:(Int)->Unit={}
    var progress:(()->Unit)?=null
    var select:(()->Unit)?=null
}
internal val LocalReaderChrome=staticCompositionLocalOf<ReaderChrome?>{null}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun PaperReaderChrome(chrome:ReaderChrome,open:OpenBook,prefs:ReaderPreferences,update:(ReaderPreferences)->Unit,back:()->Unit,navigation:()->Unit,settings:()->Unit,bookmark:()->Unit,notes:()->Unit,assets:()->Unit,mediaCount:Int) {
    var brightness by remember{mutableStateOf(false)}
    BackHandler(chrome.visible){chrome.visible=false}
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(chrome.visible,enter=fadeIn()+slideInVertically{ -it/3 },exit=fadeOut(),modifier=Modifier.align(Alignment.TopCenter)) {
            ReaderPlate(true){
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                    GlyphButton("back",T("返回书架"),back)
                    Text(open.book.title,Modifier.weight(1f).padding(horizontal=12.dp),fontFamily=FontFamily.Serif,maxLines=1,overflow=TextOverflow.Ellipsis)
                    if(prefs.bookmarksEnabled)GlyphButton("bookmark",T("添加书签"),bookmark)
                    GlyphButton("more",T("阅读笔记"),notes)
                }
            }
        }
        AnimatedVisibility(chrome.visible,enter=fadeIn()+slideInVertically{it/3},exit=fadeOut(),modifier=Modifier.align(Alignment.BottomCenter)) {
            ReaderPlate(false){
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=24.dp,vertical=16.dp)){
                    var dragging by remember{mutableStateOf(false)};var target by remember(chrome.page){mutableFloatStateOf(chrome.page.toFloat())}
                    Row(verticalAlignment=Alignment.CenterVertically){Text(chrome.chapter,Modifier.weight(1f),fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis);TextButton(onClick={chrome.progress?.invoke()}){Text("${if(dragging)target.toInt() else chrome.page} / ${chrome.count}",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
                    Box(Modifier.fillMaxWidth()) {
                        Slider(value=target.coerceIn(1f,chrome.count.coerceAtLeast(2).toFloat()),onValueChange={dragging=true;target=it},onValueChangeFinished={chrome.seek(target.toInt());dragging=false},valueRange=1f..chrome.count.coerceAtLeast(2).toFloat(),enabled=chrome.count>1,
                            thumb={Box(Modifier.size(12.dp).background(PaperGold,androidx.compose.foundation.shape.CircleShape))},
                            track={Box(Modifier.fillMaxWidth().height(3.dp).background(PaperGold.copy(alpha=.18f),RoundedCornerShape(2.dp))){Box(Modifier.fillMaxWidth(((target-1)/((chrome.count-1).coerceAtLeast(1))).coerceIn(0f,1f)).fillMaxHeight().background(PaperGold,RoundedCornerShape(2.dp)))}})
                        if(dragging)Surface(Modifier.align(Alignment.TopCenter).offset(y=(-32).dp),shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceContainer,shadowElevation=4.dp){Text(T("%1\$s 页 · %2\$s").format(target.toInt(), chrome.chapter),Modifier.padding(horizontal=12.dp,vertical=8.dp),fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                        listOf(Triple("list",T("目录"),navigation),Triple("image",T("图鉴"),assets),Triple("sun",T("亮度 / 夜间"),{brightness=true}),Triple("type",T("阅读设置"),settings),Triple("bookmark",T("划词笔记"),{chrome.select?.invoke();Unit})).let{if(prefs.navigationFirst)it else it.reversed()}.forEach{(icon,title,action)->
                            val reduceMotion=rememberReducedMotion()
                            val press=rememberReaderInteraction()
                            Column(Modifier.clipForTap().readerPressScale(press,reduceMotion).clickable(interactionSource=press,indication=null,onClick=action).padding(horizontal=10.dp,vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally){
                                Box{
                                    PaperIcon(icon)
                                    // 有随附素材时给「图鉴」加一枚古金提示点。
                                    if(title==T("图鉴")&&mediaCount>0)Box(Modifier.align(Alignment.TopEnd).offset(x=3.dp,y=(-2).dp).size(6.dp).background(PaperGold,androidx.compose.foundation.shape.CircleShape))
                                }
                                Text(title,Modifier.padding(top=8.dp),fontSize=11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    if(brightness){
        val window=LocalContext.current.findActivity()?.window
        if(window!=null){
        var level by remember{mutableFloatStateOf(window.attributes.screenBrightness.takeIf{it>=0}?:.5f)}
        AlertDialog(onDismissRequest={brightness=false},title={Text(T("一盏适合此刻的光"),fontFamily=FontFamily.Serif)},text={Column{
            Text(T("屏幕亮度"));Slider(level,{level=it;window.attributes=window.attributes.apply{screenBrightness=it.coerceIn(.02f,1f)}})
            TextButton(onClick={window.attributes=window.attributes.apply{screenBrightness=-1f}}){Text(T("跟随系统亮度"))}
            Row{listOf("paper" to T("纸色"),"light" to T("日间"),"dark" to T("夜间")).forEach{(key,label)->TextButton(onClick={update(prefs.copy(theme=key))}){Text(label)}}}
        }},confirmButton={TextButton(onClick={brightness=false}){Text(T("完成"))}})
        }
    }
}
private fun Modifier.clipForTap()=this.then(Modifier.background(androidx.compose.ui.graphics.Color.Transparent,RoundedCornerShape(12.dp)))

@Composable private fun ReaderPlate(top:Boolean,content:@Composable ()->Unit){
    val shape=if(top)RoundedCornerShape(bottomStart=20.dp,bottomEnd=20.dp) else RoundedCornerShape(topStart=24.dp,topEnd=24.dp)
    Surface(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.surface,shape=shape,shadowElevation=6.dp){content()}
}
