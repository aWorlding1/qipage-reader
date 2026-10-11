package local.readapp.data

import local.readapp.core.T

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import local.readapp.core.*
import java.io.File
import java.util.UUID

class LocalBookRepository(private val context:Context, private val dao:BookDao,private val marks:BookmarkDao, private val engine:TextEngine,private val epubEngine:EpubEngine):BookRepository {
    override suspend fun editBook(id:String,title:String,author:String,description:String)=mutex.withLock {
        require(title.isNotBlank()) { T("书名不能为空") }
        val row=dao.find(id) ?: error(T("书籍不存在"))
        dao.update(row.copy(title=title.trim().take(200),author=author.trim().take(200),description=description.take(8000)))
    }
    private val root=File(context.filesDir,"books").apply { mkdirs() }
    private val mutex=Mutex()
    private val source=LocalDocumentSource(context.contentResolver)
    private var seeded=false
    override val books=flow { seedFromAssetsIfNeeded(); emitAll(dao.observe()) }.map { rows -> rows.map { row -> row.model().copy(cover=File(folder(row.id),"cover.png").takeIf { it.exists() }?.absolutePath.orEmpty()) } }
    private fun folder(id:String):File { require(id.matches(Regex("[a-f0-9-]{36}"))); return File(root,id) }

    /** 零配置：assets 根下每个含 TXT / EPUB 的文件夹＝一本书（可放图片当封面，图片同时用于章首插图）。 */
    private suspend fun seedFromAssetsIfNeeded() {
        if(seeded) return
        mutex.withLock {
            if(seeded) return@withLock
            withContext(Dispatchers.IO) {
                val folders=runCatching { context.assets.list("")?.toList().orEmpty() }.getOrDefault(emptyList())
                for(name in folders) {
                    if(name in EXCLUDED_ASSET_FOLDERS) continue
                    val key="asset:$name"
                    if(dao.byHash(key)!=null) continue
                    val files=listAssetFiles(name)
                    val texts=files.filter { it.substringAfterLast('.',"").lowercase() in TEXT_EXTENSIONS }.sorted()
                    val epubs=files.filter { it.endsWith(".epub",true) }.sorted()
                    if(texts.isEmpty()&&epubs.isEmpty()) continue
                    runCatching { seedOne(name,files,texts,epubs,key) }
                }
                seeded=true
            }
        }
    }
    private suspend fun seedOne(folderName:String,files:List<String>,texts:List<String>,epubs:List<String>,key:String) {
        val id=UUID.randomUUID().toString(); val directory=folder(id).apply { mkdirs() }
        val raw=File(directory,"source.txt")
        // 正文优先 TXT（多文件按名拼接）；只有 EPUB 的文件夹也直接成书，走 epub 引擎。
        var format="txt"; var encoding=""; var chars=0L
        if(texts.isNotEmpty()) {
            writeConcatenated(folderName,texts,raw)
            val content=runInterruptible { engine.prepare(raw,File(directory,"text")) }
            encoding=content.encoding; chars=content.length
        } else {
            format="epub"
            context.assets.open("$folderName/${epubs.first()}").use { input -> raw.outputStream().buffered().use { input.copyTo(it) } }
        }
        pickCover(files)?.let { coverName -> runCatching { writeCover("$folderName/$coverName",File(directory,"cover.png")) } }
        dao.insert(BookRow(id,deriveAssetTitle(folderName),key,"$ASSET_URI_PREFIX$folderName",true,encoding,chars,raw.length(),format=format))
    }
    /** 递归列出文件夹内全部文件（含子目录），返回相对路径，结果稳定排序。 */
    private fun listAssetFiles(folderName:String,prefix:String=""):List<String> {
        val path=if(prefix.isEmpty())folderName else "$folderName/$prefix"
        val entries=runCatching { context.assets.list(path)?.toList().orEmpty() }.getOrDefault(emptyList()).sorted()
        val result=mutableListOf<String>()
        for(entry in entries) {
            val relative=if(prefix.isEmpty())entry else "$prefix/$entry"
            val nested=runCatching { context.assets.list("$folderName/$relative")?.toList().orEmpty() }.getOrDefault(emptyList())
            if(nested.isNotEmpty()) result+=listAssetFiles(folderName,relative) else result+=relative
        }
        return result
    }
    /** 封面：优先名为 cover.* 的图（家族标准），其次按文件名取第一张，保证结果稳定。 */
    private fun pickCover(files:List<String>):String?=files
        .filter { it.substringAfterLast('.',"").lowercase() in COVER_EXTENSIONS }
        .sortedWith(compareBy({ if(it.substringBeforeLast('.').substringAfterLast('/').equals("cover",true))0 else 1 }, { it.lowercase() }))
        .firstOrNull()
    private fun writeConcatenated(folderName:String,texts:List<String>,target:File) {
        target.outputStream().buffered().use { out ->
            texts.forEachIndexed { index,name ->
                if(index>0) out.write("\n\n".toByteArray())
                context.assets.open("$folderName/$name").use { it.copyTo(out) }
            }
        }
    }
    private fun writeCover(assetPath:String,target:File) {
        val bytes=context.assets.open(assetPath).use { it.readBytes() }
        val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
        android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        if(bounds.outWidth<=0||bounds.outHeight<=0) return
        var sample=1; while(bounds.outWidth/sample>1024||bounds.outHeight/sample>1024) sample*=2
        val bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,android.graphics.BitmapFactory.Options().apply { inSampleSize=sample }) ?: return
        try { target.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) } } finally { bitmap.recycle() }
    }
    private suspend fun ensureRaw(row:BookRow,raw:File) {
        if(raw.exists()) return
        if(row.uri.startsWith(ASSET_URI_PREFIX)) restoreFromAssets(row,raw) else recoverSource(row,raw)
    }
    private fun restoreFromAssets(row:BookRow,raw:File) {
        val folderName=row.uri.removePrefix(ASSET_URI_PREFIX)
        val files=listAssetFiles(folderName)
        val texts=files.filter { it.substringAfterLast('.',"").lowercase() in TEXT_EXTENSIONS }.sorted()
        if(texts.isNotEmpty()) { writeConcatenated(folderName,texts,raw); return }
        val epub=files.firstOrNull { it.endsWith(".epub",true) } ?: error(T("内置资源缺失，请重新安装应用"))
        context.assets.open("$folderName/$epub").use { input -> raw.outputStream().buffered().use { input.copyTo(it) } }
    }
    private fun deriveAssetTitle(name:String):String=name.replace(Regex("^\\s*\\d+[._ -]*"),"").replace('_',' ').replace('-',' ').trim()
        .split(' ').joinToString(" "){it.replaceFirstChar { ch -> ch.uppercase() }}.trim()
        .ifBlank { name }

    private fun assetFolderName(row:BookRow):String?=row.uri.takeIf { it.startsWith(ASSET_URI_PREFIX) }?.removePrefix(ASSET_URI_PREFIX)
    private fun kindOf(name:String):AssetKind?=when(name.substringAfterLast('.',"").lowercase()) {
        "txt","text","md","epub"->AssetKind.Text
        "webp","png","jpg","jpeg","gif","bmp","heic","heif","avif"->AssetKind.Image
        "mp4","mkv","m4v","webm","mov","avi","ts","m2ts","3gp","flv","mpg","mpeg","ogv"->AssetKind.Video
        "mp3","m4a","m4b","aac","ogg","oga","opus","wav","flac","amr","aiff"->AssetKind.Audio
        else->null
    }
    private fun assetSize(path:String):Long =
        runCatching { context.assets.openFd(path).use { it.length } }
            .getOrElse { runCatching { context.assets.open(path).use { it.available().toLong() } }.getOrDefault(0L) }
    private fun mimeOf(name:String):String =
        android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.',"").lowercase()) ?: "application/octet-stream"

    override suspend fun assets(id:String):List<BookAsset> = withContext(Dispatchers.IO) {
        val row=dao.find(id) ?: return@withContext emptyList()
        val folderName=assetFolderName(row) ?: return@withContext emptyList()
        runCatching {
            listAssetFiles(folderName)
                .mapNotNull { name -> kindOf(name)?.let { BookAsset(name,it,assetSize("$folderName/$name")) } }
                .sortedWith(compareBy({ it.kind.ordinal }, { it.name.lowercase() }))
        }.getOrDefault(emptyList())
    }
    /** 按 assets 里的原文件名写入公共 Download 目录（API 29+ 无需权限）。 */
    private fun writeAssetToDownloads(assetPath:String,fileName:String):String {
        // Android 10 以下没有 Downloads 集合：回退到应用专属外部下载目录（家族统一约定）。
        if(android.os.Build.VERSION.SDK_INT<android.os.Build.VERSION_CODES.Q) {
            val dir=context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            dir.mkdirs()
            val target=File(dir,fileName)
            context.assets.open(assetPath).use { input -> target.outputStream().buffered().use { input.copyTo(it) } }
            return target.name
        }
        val resolver=context.contentResolver
        val values=android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME,fileName)
            put(android.provider.MediaStore.Downloads.MIME_TYPE,mimeOf(fileName))
            put(android.provider.MediaStore.Downloads.IS_PENDING,1)
        }
        val uri=resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values) ?: error(T("无法创建下载条目"))
        try {
            resolver.openOutputStream(uri)?.use { out -> context.assets.open(assetPath).use { it.copyTo(out) } } ?: error(T("无法写入下载文件"))
            values.clear(); values.put(android.provider.MediaStore.Downloads.IS_PENDING,0)
            resolver.update(uri,values,null,null)
        } catch(e:Exception) { runCatching { resolver.delete(uri,null,null) }; throw e }
        return fileName
    }
    private fun safeName(name:String)=name.replace('/','_').replace('\\','_')
    override suspend fun materializeAsset(id:String,name:String):String = withContext(Dispatchers.IO) {
        val row=dao.find(id) ?: error(T("书籍不存在"))
        val folderName=assetFolderName(row) ?: error(T("这本书没有随附素材"))
        val file=safeName(name)
        val target=File(File(File(context.cacheDir,"reader-assets"),row.id),file)
        target.parentFile?.mkdirs()
        if(!target.exists() || target.length()==0L) {
            val temp=File(target.parentFile,"$file.part")
            context.assets.open("$folderName/$name").use { input -> temp.outputStream().buffered().use { input.copyTo(it) } }
            if(target.exists()) target.delete()
            check(temp.renameTo(target)) { T("无法准备素材") }
        }
        target.absolutePath
    }
    override suspend fun exportAsset(id:String,name:String):String = withContext(Dispatchers.IO) {
        val row=dao.find(id) ?: error(T("书籍不存在"))
        val folderName=assetFolderName(row) ?: error(T("这本书没有随附素材"))
        writeAssetToDownloads("$folderName/$name",name.substringAfterLast('/'))
    }
    override suspend fun exportAll(id:String):String = withContext(Dispatchers.IO) {
        val list=assets(id)
        require(list.isNotEmpty()) { T("这本书没有随附素材") }
        var saved=0; val failed=mutableListOf<String>()
        for(asset in list) runCatching { exportAsset(id,asset.name) }.onSuccess { saved++ }.onFailure { failed.add(asset.name) }
        buildString {
            append(T("已保存 %1\$s 个文件到 Download").format(saved))
            if(failed.isNotEmpty()) append(T("；%1\$s 个失败").format(failed.size))
        }
    }
    private fun persist(uri:Uri, flags:Int):Boolean {
        if(flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION == 0) return false
        return runCatching { context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION); true }.getOrDefault(false)
    }
    private suspend fun recoverSource(row:BookRow,raw:File) {
        val staging=File(raw.parentFile,"source-recovery.tmp")
        try {
            val copied=withTimeout(60_000) { source.copy(Uri.parse(row.uri),staging) }
            require(copied.hash==row.hash) { T("原文件内容已改变，请作为新书重新导入") }
            check(staging.renameTo(raw)) { T("无法恢复本机副本") }
        } finally { staging.delete() }
    }
    override suspend fun importBook(uri:String,grantFlags:Int):ImportResult = mutex.withLock { withContext(Dispatchers.IO) {
        val id=UUID.randomUUID().toString(); val directory=folder(id).apply { mkdirs() }; val raw=File(directory,"source.txt")
        var committed=false
        try {
            val address=Uri.parse(uri); val copied=withTimeout(60_000) { source.copy(address,raw) }
            dao.byHash(copied.hash)?.let { return@withContext ImportResult(it.model(),true) }
            val content=if(copied.format=="txt")runInterruptible { engine.prepare(raw,File(directory,"text")) } else null
            var title=copied.name.replace(Regex("(?i)\\.(txt|epub)$"),"");var author=""
            if(copied.format=="epub")runInterruptible { epubEngine.open(raw).use { epub ->
                title=epub.title;author=epub.author
                // Validate the first chapter before registering the book; later chapters load on demand.
                epub.chapter(epub.chapters.first().path)
                epub.cover()?.second?.let { bytes ->
                    val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                    if(bounds.outWidth>0 && bounds.outHeight>0){
                        var sample=1;while(bounds.outWidth/sample>256 || bounds.outHeight/sample>256)sample*=2
                        val options=android.graphics.BitmapFactory.Options().apply { inSampleSize=sample }
                        android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)?.let { bitmap ->
                            try{File(directory,"cover.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }}finally{bitmap.recycle()}
                        }
                    }
                }
            } }
            ensureActive()
            // Persisting a grant, inserting metadata and choosing reference/copy form one commit step.
            val row=withContext(NonCancellable) {
                val referenced=persist(address,grantFlags) && copied.format=="txt"
                val row=BookRow(id,title,copied.hash,uri,!referenced,content?.encoding?:"",content?.length?:0,copied.bytes,format=copied.format,author=author)
                dao.insert(row); committed=true
                if(referenced) raw.delete()
                row
            }
            ImportResult(row.model(),false)
        } finally { if(!committed) directory.deleteRecursively() }
    } }
    override suspend fun openBook(id:String):OpenBook = mutex.withLock { withContext(Dispatchers.IO) {
        val row=dao.find(id) ?: error(T("这本书已移出书架"))
        if(row.format=="epub"){
            val raw=File(folder(id),"source.txt");ensureRaw(row,raw)
            val epub=runInterruptible { epubEngine.open(raw) }
            dao.locator(id,row.locator,System.currentTimeMillis())
            return@withContext OpenBook(row.model(),epub=epub)
        }
        val cache=File(folder(id),"text")
        val content=try { engine.reopen(cache,row.encoding) } catch (_:Exception) {
            val raw=File(folder(id),"source.txt")
            try {
                ensureRaw(row,raw)
                runInterruptible { engine.prepare(raw,cache,row.encoding) }
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { throw IllegalStateException(T("无法恢复正文，请重新选择原 TXT 文件。%1\$s").format(e.message.orEmpty()),e) }
            finally { if(!row.copied) raw.delete() }
        }
        dao.progress(id,row.position.coerceIn(0,content.length),System.currentTimeMillis())
        runInterruptible { content.toc };OpenBook(row.model(),content)
    } }
    override suspend fun savePosition(id:String,position:Long) {
        val row=dao.find(id) ?: return
        dao.progress(id,position.coerceIn(0,row.chars),System.currentTimeMillis())
    }
    override suspend fun removeBook(id:String)=mutex.withLock { withContext(Dispatchers.IO) {
        dao.delete(id); folder(id).deleteRecursively(); Unit
    } }
    override suspend fun changeEncoding(id:String,encoding:String):OpenBook=mutex.withLock { withContext(Dispatchers.IO) {
        require(encoding in setOf("UTF-8","GB18030","UTF-16LE","UTF-16BE"))
        val row=dao.find(id) ?: error(T("书籍不存在"))
        val directory=folder(id); val raw=File(directory,"source.txt"); val newCache=File(directory,"text-new"); val oldCache=File(directory,"text")
        try {
            ensureRaw(row,raw)
            val content=runInterruptible { engine.prepare(raw,newCache,encoding) }
            withContext(NonCancellable) {
                val backup=File(directory,"text-old"); backup.deleteRecursively()
                check(oldCache.renameTo(backup)); check(newCache.renameTo(oldCache))
                try { dao.update(row.copy(encoding=content.encoding,chars=content.length,position=0,locator="")) }
                catch(e:Exception) { oldCache.deleteRecursively(); backup.renameTo(oldCache); throw e }
                backup.deleteRecursively()
            }
            val next=dao.find(id) ?: error(T("书籍不存在"));val reopened=engine.reopen(oldCache,next.encoding);reopened.toc; OpenBook(next.model(),reopened)
        } finally { newCache.deleteRecursively(); if(!row.copied) raw.delete() }
    } }
    override suspend fun reconnect(id:String,uri:String,grantFlags:Int)=mutex.withLock { withContext(Dispatchers.IO) {
        val row=dao.find(id) ?: error(T("书籍不存在"))
        val temp=File.createTempFile("relink-",".txt",context.cacheDir)
        try {
            val address=Uri.parse(uri); val copied=source.copy(address,temp)
            require(copied.hash==row.hash) { T("选择的不是原文件；内容不同的书请通过导入添加") }
            withContext(NonCancellable) {
                val referenced=persist(address,grantFlags) && row.format=="txt"
                if(!referenced) temp.copyTo(File(folder(id),"source.txt"),overwrite=true)
                dao.update(row.copy(uri=uri,copied=!referenced))
            }
        } finally { temp.delete() }
    } }
    override suspend fun saveLocator(id:String,locator:Locator) {
        if(locator.format=="txt")savePosition(id,locator.offset)
        else dao.locator(id,LocatorCodec.encode(locator),System.currentTimeMillis())
    }
    override fun bookmarks(id:String)=marks.observe(id).map { rows -> rows.map { Bookmark(it.id,it.bookId,it.title,LocatorCodec.decode(it.locator),it.createdAt) } }
    override suspend fun addBookmark(id:String,title:String,locator:Locator) { marks.insert(BookmarkRow(UUID.randomUUID().toString(),id,title.take(160),LocatorCodec.encode(locator),System.currentTimeMillis())) }
    override suspend fun removeBookmark(id:String)=marks.delete(id)

    companion object {
        private const val ASSET_URI_PREFIX="asset:///"
        private val COVER_EXTENSIONS=setOf("webp","png","jpg","jpeg","gif","bmp","heic","heif","avif")
        private val TEXT_EXTENSIONS=setOf("txt","text","md")
        private val EXCLUDED_ASSET_FOLDERS=setOf("brand","brand-assets","images","fonts","webkit","shaders","licenses","META-INF","i18n")
    }
}
