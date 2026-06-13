package com.shortvideocleaner.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.shortvideocleaner.app.model.AppInfo

/**
 * 短视频 + 资讯类应用检测器 — 识别短视频/短剧/新闻资讯平台并引导卸载
 */
object VideoAppDetector {

    /**
     * 已知短视频 + 资讯类应用包名库
     *
     * 覆盖：抖音系、快手系、腾讯系、字节系、小红书、新闻资讯、短剧等
     */
    private val SHORT_VIDEO_PACKAGES: Set<String> = setOf(
        // ══════════════════════════════════════
        // 短视频
        // ══════════════════════════════════════

        // ── 抖音系 ──
        "com.ss.android.ugc.aweme",           // 抖音
        "com.ss.android.ugc.aweme.lite",      // 抖音极速版
        "com.ss.android.ugc.aweme.neptune",   // 抖音火山版
        "com.ss.android.ugc.trill",           // TikTok
        "com.zhiliaoapp.musically",           // TikTok (旧包名)

        // ── 快手系 ──
        "com.kuaishou.nebula",                // 快手
        "com.kuaishou.nebula.lite",           // 快手极速版
        "com.kuaishou.hlf",                   // 快手概念版
        "com.kwai.video",                     // Kwai (国际版)

        // ── 腾讯系 ──
        "com.tencent.weishi",                 // 微视
        "com.tencent.mv",                     // 微视极速版
        "com.tencent.qqlive",                 // 腾讯视频 (含短视频)

        // ── 字节系其他 ──
        "com.ss.android.article.video",       // 西瓜视频
        "com.ss.android.article.video.lite",  // 西瓜视频极速版
        "com.ss.android.yumme.video",         // 抖音精选
        "com.ss.android.ugc.live",            // 抖音直播

        // ── 小红书 ──
        "com.xingin.xhs",                     // 小红书

        // ── 百度系 ──
        "com.baidu.haokan",                   // 好看视频

        // ── 海外短视频 ──
        "com.google.android.youtube",         // YouTube (含 Shorts)
        "com.instagram.android",              // Instagram (含 Reels)
        "com.snapchat.android",               // Snapchat

        // ── 其他短视频 ──
        "com.yixia.videoeditor",              // 秒拍
        "com.meitu.meipaimv",                 // 美拍
        "com.lemon.lv",                       // Lemon8
        "com.luna.music",                     // 汽水音乐（抖音关联）

        // ── 短剧/小说（含短剧）──
        "com.phoenix.read",                   // 番茄畅听/短剧
        "com.ss.android.article.lite",        // 番茄小说（含短剧）
        "com.zhangyue.read.iReader",          // 掌阅（含短剧）
        "com.qidian.QDReader",                // 起点读书（含短剧）

        // ══════════════════════════════════════
        // 新闻资讯
        // ══════════════════════════════════════

        // ── 今日头条系 ──
        "com.ss.android.article.news",        // 今日头条
        "com.ss.android.article.lite",        // 今日头条极速版

        // ── 腾讯新闻 ──
        "com.tencent.news",                   // 腾讯新闻
        "com.tencent.news.lite",              // 腾讯新闻极速版

        // ── 网易新闻 ──
        "com.netease.newsreader.activity",    // 网易新闻
        "com.netease.news",                   // 网易新闻 (新版)

        // ── 搜狐新闻 ──
        "com.sohu.newsclient",                // 搜狐新闻

        // ── 新浪新闻 ──
        "com.sina.news",                      // 新浪新闻

        // ── 凤凰新闻 ──
        "com.ifeng.news2",                    // 凤凰新闻
        "com.ifeng.newsi",                    // 凤凰新闻 (旧版)

        // ── 一点资讯 ──
        "com.yidian.zixun",                   // 一点资讯

        // ── 趣头条 ──
        "com.jifen.qukan",                    // 趣头条

        // ── 天天快报 ──
        "com.tencent.reading",                // 天天快报

        // ── UC浏览器（UC头条）──
        "com.UCMobile",                       // UC浏览器
        "com.UCMobile.intl",                  // UC浏览器国际版

        // ── 百度 ──
        "com.baidu.news",                     // 百度新闻
        "com.baidu.searchbox",                // 百度 (含信息流)

        // ── 知乎 ──
        "com.zhihu.android",                  // 知乎

        // ── 微博 ──
        "com.sina.weibo",                     // 微博

        // ── 豆瓣 ──
        "com.douban.frodo",                   // 豆瓣

        // ── 澎湃新闻 ──
        "com.wondertek.paper",                // 澎湃新闻

        // ── 界面新闻 ──
        "com.jiemian.news",                   // 界面新闻

        // ── 华尔街见闻 ──
        "com.wallstreetcn.news",              // 华尔街见闻

        // ── 观察者网 ──
        "com.guancha.app",                    // 观察者网

        // ── 人民日报 ──
        "com.peopledailychina.activity",      // 人民日报

        // ── 新华社 ──
        "com.xinhua.news",                    // 新华社
        "cn.xinhua.news",                     // 新华社 (新版)

        // ── CCTV / 央视新闻 ──
        "com.cctv.news",                      // 央视新闻
        "com.cctv.newmedia",                  // 央视频

        // ── 环球时报 ──
        "com.huanqiu.news",                   // 环球时报

        // ── 参考消息 ──
        "com.cankaoxiaoxi.app",               // 参考消息

        // ── 36氪 ──
        "com.android36kr.app",                // 36氪

        // ── 虎嗅 ──
        "com.huxiu.app",                      // 虎嗅

        // ── 钛媒体 ──
        "com.tmtpost.app",                    // 钛媒体

        // ── ZAKER ──
        "com.myzaker.ZAKER_Phone",            // ZAKER新闻
        "com.zaker.android",                  // ZAKER (旧版)

        // ── 好奇心日报 ──
        "com.qdaily.QDaily",                  // 好奇心日报

        // ── 快报/其他资讯 ──
        "com.tencent.kuikly",                 // 腾讯快报
        "com.ifeng.news",                     // 凤凰新闻极速版
        "com.bjbycx.toutiao",                 // 趣看天下

        // ── 懂车帝（字节系资讯）──
        "com.ss.android.auto",                // 懂车帝

        // ══════════════════════════════════════
        // 成人 / 私密内容 相关
        // ══════════════════════════════════════

        // ── 私密相册 / 隐藏文件应用 ──
        "com.keepsafe.vault",                 // KeepSafe 私密相册
        "com.keepsafe.calculator",            // 伪装计算器隐藏应用
        "com.calculator.vault",               // 计算器保险箱
        "com.hidephotos.vault",               // 隐藏照片保险箱
        "com.gallery.lock",                   // 相册锁
        "com.private.album",                  // 私密相册
        "com.secret.vault",                   // 秘密保险箱
        "com.domobile.applock",               // 应用锁
        "com.lock.app",                       // 应用锁 (通用)
        "com.sp.protector.free",              // 应用保护
        "com.app.protector",                  // 应用守护

        // ── 成人内容平台 ──
        "com.pornhub.android",                // Pornhub
        "com.xvideos.android",                // XVideos
        "com.redtube.android",                // Redtube
        "com.youporn.android",                // YouPorn
        "com.xhamster.android",               // XHamster
        "com.brazzers.android",               // Brazzers
        "org.adultswim",                       // 成人内容类
        "xxx.video.player",                   // XXX 视频播放器
        "com.adult.video",                    // 成人视频
        "com.sexy.video",                     // 色情视频
        "com.hot.video",                      // 热辣视频
        "com.love.video",                     // 爱情视频

        // ── 国内成人/色情直播 ──
        "com.mimi.live",                      // 咪咪直播
        "com.huya.live",                      // 虎牙（含擦边内容）
        "com.yy.live",                        // YY 直播
        "com.douyu.live",                     // 斗鱼
        "com.inke.live",                      // 映客
        "com.huajiao.live",                   // 花椒直播
        "com.zb.live",                        // 直播类通用
        "com.show.live",                      // 秀场直播
        "com.night.live",                     // 夜场直播
        "com.adult.live",                     // 成人直播

        // ── 91 / 1024 系成人平台 ──
        "com.ninetyone.android",              // 91 成人
        "com.onezerotwofour.android",         // 1024
        "com.caoliu.android",                 // 草榴社区
        "com.sex8.android",                   // 性吧
        "com.t66y.android",                   // 草榴
        "com.clsq.android",                   // 成人社区
        "com.sexinsex.android",               // 色中色
        "com.mayalive.android",               // 麻豆
        "com.madou.android",                  // 麻豆传媒
        "com.javbus.android",                 // JavBus
        "com.javlibrary.android",             // JavLibrary

        // ── 成人游戏 ──
        "com.hentai.game",                    // 成人游戏
        "com.nutaku.android",                 // Nutaku 成人游戏平台
        "com.ero.game",                       // 工口游戏
        "com.adult.game",                     // 成人游戏
        "com.sexy.game",                      // 性感游戏

        // ── 成人聊天 / 约会 ──
        "com.tinder.android",                 // Tinder
        "com.momo.im",                        // 陌陌
        "com.tantan.android",                 // 探探
        "com.soul.android",                   // Soul
        "com.blued.android",                  // Blued
        "com.grindr.android",                 // Grindr
        "com.jackd.android",                  // Jack'd
        "com.hornet.android",                 // Hornet
        "com.badoo.android",                  // Badoo
        "com.meetme.android",                 // MeetMe

        // ── 成人漫画/动漫 ──
        "com.hentai.manga",                   // 成人漫画
        "com.comic.hentai",                   // H漫画
        "com.adult.manga",                    // 成人漫画
        "com.doujin.manga",                   // 同人漫画

        // ══════════════════════════════════════
        // 视频播放器（常用于播放下载视频）
        // ══════════════════════════════════════

        "com.mxtech.videoplayer.ad",          // MX Player
        "com.mxtech.videoplayer.pro",         // MX Player Pro
        "org.videolan.vlc",                   // VLC
        "com.kmplayer",                       // KMPlayer
        "com.bsplayer.bspandroid.free",       // BSPlayer
        "com.xvideoplayer",                   // X-Video Player
        "com.gomplayer",                      // GOM Player
        "com.molotov",                        // Molotov
        "com.archos.mediacenter.video",       // Archos Video
        "video.player.videoplayer",           // 通用视频播放器
        "com.video.player",                   // 视频播放器
        "com.mvideoplayer",                   // M视频播放器
        "com.hdmovies",                       // HD Movies
        "com.fast.player",                    // Fast Player
        "com.kk.player",                      // KK Player
        "com.player.mv",                      // MV Player

        // ── 国内视频播放器 ──
        "com.tencent.qqlive",                 // 腾讯视频
        "com.qiyi.video",                     // 爱奇艺
        "com.youku.phone",                    // 优酷

        "com.sohu.sohuvideo",                 // 搜狐视频
        "com.hunantv.imgo.activity",          // 芒果TV
        "com.le123.ysdq",                     // 乐视视频
        "com.cmcc.video",                     // 咪咕视频
        "com.pplive.androidphone",            // PPTV

        // ── 下载管理器 ──
        "com.xunlei.downloadprovider",        // 迅雷
        "com.xunlei.thunder",                 // 迅雷极速版
        "org.thunderdog.challegram",          // 迅雷相关
        "com.flashget.android",               // 快车下载
        "com.utorrent.client",                // uTorrent
        "com.bittorrent.client",              // BitTorrent
        "org.transdroid",                     // Transdroid
        "com.dv.adm",                         // ADM 下载器
        "idm.internet.download.manager",      // IDM
        "com.download.manager",               // 通用下载器

    )

    /**
     * 从已安装应用列表中筛选出短视频应用
     */
    fun detect(installedApps: List<AppInfo>): List<AppInfo> {
        return installedApps.filter { app ->
            SHORT_VIDEO_PACKAGES.contains(app.packageName)
        }
    }

    /**
     * 引导卸载指定应用 — 多方案兜底，适配 Vivo/OPPO 等魔改 ROM
     */
    fun uninstallApp(context: Context, packageName: String) {
        // 方案 1：标准 ACTION_UNINSTALL_PACKAGE（纯 Android 12+）
        try {
            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                data = Uri.parse("package:$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return
            }
        } catch (_: Exception) {}

        // 方案 2：ACTION_DELETE（老版本 / Vivo / OPPO）
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return
        } catch (_: Exception) {}

        // 方案 3：应用详情页（最后兜底，用户手动点卸载）
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    /**
     * 检查是否是已知的短视频包名
     */
    fun isShortVideoPackage(packageName: String): Boolean {
        return SHORT_VIDEO_PACKAGES.contains(packageName)
    }
}
