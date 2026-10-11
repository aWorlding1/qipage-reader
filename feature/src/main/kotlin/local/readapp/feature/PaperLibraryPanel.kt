package local.readapp.feature

import local.readapp.core.T

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.first
import local.readapp.core.*

@Composable internal fun PaperLibraryPanel(title:String,books:List<Book>,prefs:ReaderPreferences,update:(ReaderPreferences)->Unit,repository:BookRepository,onOpen:(String)->Unit,onOpenGuide:()->Unit,close:()->Unit){
    val context=LocalContext.current
    if(title==T("主题与排版")){
        Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)){ReaderSettings(prefs,null,update,{},close)}
        return
    }
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxSize()){
            Column(Modifier.safeDrawingPadding().padding(24.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){GlyphButton("back",T("返回"),close);Text(title,Modifier.weight(1f),fontFamily=FontFamily.Serif,fontSize=23.sp)}
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top=28.dp)){
                    when(title){
                        T("阅读统计")->{
                            val seconds=context.getSharedPreferences("paper-ui",0).getLong("readingSeconds",0)
                            Text(T("%1\$s 小时 %2\$s 分钟").format(seconds/3600, (seconds%3600)/60),fontFamily=FontFamily.Serif,fontSize=30.sp)
                            Text(T("累计前台阅读时长"),Modifier.padding(top=12.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(32.dp));Text(T("藏书 %1\$s 本 · 已开始 %2\$s 本").format(books.size, books.count{it.lastRead>0}))
                            Text(T("从此版本开始记录；历史阅读时长未作推算。"),Modifier.padding(top=20.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        T("书签与笔记")->{
                            val marks by produceState<List<Pair<Book,Bookmark>>>(emptyList(),books){value=books.flatMap{book->repository.bookmarks(book.id).first().map{book to it}}}
                            Text(T("书签"),fontSize=20.sp,fontFamily=FontFamily.Serif)
                            if(marks.isEmpty())Text(T("在阅读页轻点书签，收藏此刻。"),Modifier.padding(vertical=20.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
                            marks.forEach{(book,mark)->TextButton(onClick={context.getSharedPreferences("paper-ui",0).edit().putString("pending-bookmark",mark.id).apply();onOpen(book.id);close()}){Column(Modifier.fillMaxWidth()){Text(mark.title.ifBlank{book.title});Text(book.title,fontSize=12.sp)}}}
                            Spacer(Modifier.height(24.dp));Text(T("笔记"),fontSize=20.sp,fontFamily=FontFamily.Serif)
                            PaperNotes(books.map{it.id}.toSet(),onOpen)
                        }
                        T("云端同步")->{PaperIcon("cloud",Modifier.size(40.dp),PaperGold);Spacer(Modifier.height(24.dp));Text(T("把安静，留在本机。"),fontFamily=FontFamily.Serif,fontSize=24.sp);Text(T("云端同步尚未启用。书架、书签与笔记保存在此设备中，当前版本不连接网络。"),Modifier.padding(top=20.dp))}
                        else->{
                            var license by remember{mutableStateOf<String?>(null)}
                            var showLicenses by remember{mutableStateOf(false)}
                            var showChangelog by remember{mutableStateOf(false)}
                            Text("光栖书斋",fontFamily=FontFamily.Serif,fontSize=28.sp)
                            Text(T("版本 1.0.0（Build 100）"),Modifier.padding(top=10.dp),fontSize=12.sp,color=PaperGold)
                            Spacer(Modifier.height(24.dp))
                            AboutPoint("book",T("内置藏书"),T("随应用附带的《午夜书房》样章与封面素材，开箱即读，也可一键导出到「下载」目录。"))
                            AboutPoint("type",T("通用阅读"),T("也支持导入本机 TXT 与 EPUB，可调排版、翻页方式与配色，长按正文即可高亮、摘录。"))
                            AboutPoint("lock",T("私域优先"),T("书架、书签、笔记与阅读时长只保存在本机私有空间，不登录、不上传、不随云备份带出。"))
                            Spacer(Modifier.height(22.dp));HorizontalDivider();Spacer(Modifier.height(16.dp))
                            OutlinedButton(onClick=onOpenGuide,modifier=Modifier.fillMaxWidth()){Text(T("新手引导"))}
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick={showLicenses=true},modifier=Modifier.fillMaxWidth()){Text(T("开源许可"))}
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick={showChangelog=true},modifier=Modifier.fillMaxWidth()){Text(T("版本与更新"))}
                            if(showLicenses)AlertDialog(
                                onDismissRequest={showLicenses=false},
                                title={Text(T("开源许可"))},
                                text={Column{Text(T("本应用基于 AndroidX、Jetpack Compose、Room 与 jsoup 等开源项目构建，各组件分别采用 Apache License 2.0 等许可。完整许可文本随应用提供，可在下方查阅。"));Spacer(Modifier.height(12.dp));listOf("NOTICE.txt","Apache-2.0.txt","Desugar-GPL2-Classpath.txt").forEach{file->TextButton(onClick={license=file}){Text(file)}}}},
                                confirmButton={TextButton(onClick={showLicenses=false}){Text(T("知道了"))}})
                            if(showChangelog)AlertDialog(
                                onDismissRequest={showChangelog=false},
                                title={Text(T("版本与更新"))},
                                text={Text(T("1.0.0（Build 100）\n首个公开发布版本。\n\n· 新增新手引导，可在「关于」中随时回看\n· 内置《午夜书房》样章，随附封面与素材可一键下载到本机\n· 支持本机 TXT / EPUB 导入，排版、翻页方式与配色皆可自定\n· 全文搜索、书签、高亮与摘录统一收敛在本机\n\n更早的内部迭代版本未对外发布，此处不再列出。"))},
                                confirmButton={TextButton(onClick={showChangelog=false}){Text(T("知道了"))}})
                            license?.let{LicenseText(it){license=null}}
                        }
                    }
                }
            }
        }
    }
}

/** 「关于」里的一条卖点：图标 + 标题 + 说明，贴合纸张气质。 */
@Composable private fun AboutPoint(icon:String,title:String,body:String){
    Row(Modifier.padding(vertical=9.dp)) {
        PaperIcon(icon,Modifier.size(20.dp),PaperGold)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title,fontSize=14.sp,fontWeight=FontWeight.Bold)
            Text(body,Modifier.padding(top=4.dp),fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
