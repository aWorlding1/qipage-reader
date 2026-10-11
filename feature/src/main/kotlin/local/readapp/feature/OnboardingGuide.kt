package local.readapp.feature

import local.readapp.core.T

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import local.readapp.core.*

/**
 * 光栖书斋新手引导：首次启动自动展示一次，之后可从「关于 → 新手引导」随时回看。
 *
 * 五页结构：私域定位 → 内置藏书 → 下载内置内容 → 通用阅读能力 → 随时回看。
 * 观感贴合纸张气质：襁褓色底、serif 标题、古金点缀，不使用 Material 弹窗的默认样式。
 */
private data class GuidePage(val icon:String,val title:String,val body:String,val downloadTips:Boolean=false)

private val guidePages=listOf(
    GuidePage("book",T("欢迎使用光栖书斋"),T("这是一个「私域阅读空间」：书随应用一起交付，阅读全程在你自己的设备上完成，不登录、不上传、不依赖云端。")),
    GuidePage("type",T("开箱即读的内置藏书"),T("应用已附带一本精心排版的样章《午夜书房》，带封面与章节，打开就能读；它安放在应用自己的空间里，不会混进手机相册。")),
    GuidePage("download",T("把内置内容下载到本地"),T("内置藏书与随附素材（封面、插图、配音）都能随时导出到「下载」目录，文件名保持原样，换设备或清缓存也不怕丢。"),true),
    GuidePage("settings",T("它也是一台称手的阅读器"),T("除了内置藏书，你还能导入本机的 TXT 与 EPUB，调节排版、翻页方式与配色，长按正文即可高亮、摘录。")),
    GuidePage("menu",T("随时回来查看"),T("以后想再看这份引导，打开书架菜单里的「关于 → 新手引导」即可。"))
)

@Composable internal fun OnboardingGuide(onFinish:()->Unit){
    var page by remember{mutableIntStateOf(0)}
    Dialog(onDismissRequest=onFinish,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxSize()){
            Column(Modifier.safeDrawingPadding().fillMaxSize().padding(horizontal=32.dp,vertical=24.dp)){
                Spacer(Modifier.height(12.dp))
                Text("光栖书斋",fontFamily=FontFamily.Serif,fontSize=13.sp,letterSpacing=4.sp,color=PaperGold)
                Spacer(Modifier.height(4.dp))
                Text(T("新手引导"),fontFamily=FontFamily.Serif,fontSize=26.sp,letterSpacing=2.sp)
                Spacer(Modifier.weight(1f))
                AnimatedContent(
                    targetState=page,
                    transitionSpec={
                        (fadeIn(tween(ReaderMotion.STATE_MILLIS,easing=FastOutSlowInEasing)) + slideInHorizontally{it/6}) togetherWith
                        (fadeOut(tween(ReaderMotion.EXIT_MILLIS)) + slideOutHorizontally{-it/6})
                    },
                    label="guide_page"
                ){p->
                    val shown=guidePages[p.coerceIn(0,guidePages.lastIndex)]
                    Column{
                        Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(PaperGold.copy(alpha=.12f)),contentAlignment=Alignment.Center){PaperIcon(shown.icon,Modifier.size(34.dp),PaperGold)}
                        Spacer(Modifier.height(28.dp))
                        Text(shown.title,fontFamily=FontFamily.Serif,fontSize=23.sp,lineHeight=32.sp)
                        Spacer(Modifier.height(16.dp))
                        Text(shown.body,fontSize=14.sp,lineHeight=24.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(shown.downloadTips){
                            Spacer(Modifier.height(22.dp))
                            Surface(shape=RoundedCornerShape(18.dp),color=PaperGold.copy(alpha=.10f)){
                                Column(Modifier.padding(18.dp)){
                                    Text(T("两个入口都能下载"),fontWeight=FontWeight.Bold,fontSize=13.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Text(T("① 阅读页底部工具条的「图鉴」，点右上角「全部下载」\n② 打开图鉴后，每个素材右上角的下载按钮"),fontSize=12.sp,lineHeight=20.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Text("${page+1} / ${guidePages.size}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    if(page<guidePages.lastIndex)TextButton(onClick=onFinish){Text(T("跳过"))}
                    Spacer(Modifier.width(8.dp))
                    Button(onClick={if(page<guidePages.lastIndex)page++ else onFinish()},shape=RoundedCornerShape(22.dp)){Text(if(page<guidePages.lastIndex)T("下一步") else T("开始使用"))}
                }
            }
        }
    }
}
