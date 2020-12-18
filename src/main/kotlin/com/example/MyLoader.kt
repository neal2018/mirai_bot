package com.example

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import kotlinx.coroutines.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.event.events.MemberJoinEvent
import net.mamoe.mirai.event.subscribeAlways
import net.mamoe.mirai.event.subscribeMessages
import net.mamoe.mirai.join
import net.mamoe.mirai.message.data.content
import java.io.File
import java.net.URL
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


suspend fun main() {
    val dataPath = System.getProperty("user.dir") + File.separator + "src/main/kotlin/com/example/cardData"
    val qqId = 2221744851L//Bot的QQ号，需为Long类型，在结尾处添加大写L
    val password = "*****"//Bot的密码
    val groups = listOf(945408322L)
    val miraiBot = Bot(qqId, password) {
        fileBasedDeviceInfo("device.json") // 使用 "device.json" 保存设备信息
    }.alsoLogin()//新建Bot并登录
    miraiBot.subscribeMessages {
        (startsWith("/searchcard") or startsWith("/sc")) {
            reply(getSearchCardMessage(message.content, dataPath))
        }

        (startsWith("/info")) {
            reply(getInfoMessage(message.content, dataPath))
        }

        (startsWith("/help") or startsWith("/h")) {
            reply(getHelpMessage(dataPath))
        }

        (startsWith("/welcome") or startsWith("/w")) {
            reply(getWelcomeMessage(dataPath))
        }

        (startsWith("/baidu") or startsWith("/bd")) {
            reply(getSearchBaiduMessage(message.content))
        }

        (startsWith("/online") or startsWith("/ol")) {
            reply(getOnlineMessage())
        }

        (startsWith("/api") or startsWith("/Api") or startsWith("/API")) {
            reply(getAPIMessage())
        }

        (startsWith("/update")) {
            updateJson(dataPath)
            reply("更新中...")
        }
    }

    miraiBot.subscribeAlways<MemberJoinEvent> {
        it.group.sendMessage(getWelcomeMessage(dataPath))
    }

    val cal = Calendar.getInstance()
    cal[Calendar.HOUR_OF_DAY] = 8
    cal[Calendar.MINUTE] = 0
    cal[Calendar.SECOND] = 0
    val morningDate: Date = cal.time
    Timer().scheduleAtFixedRate(object : TimerTask() {
        override fun run() {
            GlobalScope.launch { sendMorningMessage(miraiBot, groups, dataPath) }
        }
    }, morningDate, 24 * 60 * 60 * 1000)

    val nightCal = Calendar.getInstance()
    nightCal[Calendar.HOUR_OF_DAY] = 23
    nightCal[Calendar.MINUTE] = 50
    nightCal[Calendar.SECOND] = 0
    val nightDate: Date = cal.time
    Timer().scheduleAtFixedRate(object : TimerTask() {
        override fun run() {
            GlobalScope.launch { sendNightMessage(miraiBot, groups, dataPath) }
        }
    }, nightDate, 24 * 60 * 60 * 1000)

    miraiBot.join() // 等待 Bot 离线, 避免主线程退出
}

fun updateJson(dataPath: String) {
//    Runtime.getRuntime().exec(dataPath + File.separator + "update.sh")
}

suspend fun sendMorningMessage(bot: Bot, groups: List<Long>, dataPath: String) {
    val info = loadJson(dataPath, "info")
    for (group in groups) {
        bot.getGroup(group).sendMessage(info["morning"] as String)
    }
}

suspend fun sendNightMessage(bot: Bot, groups: List<Long>, dataPath: String) {
    val info = loadJson(dataPath, "info")
    for (group in groups) {
        bot.getGroup(group).sendMessage(info["night"] as String)
        bot.getGroup(group).sendMessage("下面是今天的对局环境情况\n" + getAPIMessage())
    }
}


fun loadJson(dataPath: String, name: String): JsonObject {
    val fileContent = File(dataPath + File.separator + name + ".json").readText(Charsets.UTF_8)

    val parser: Parser = Parser.default()
    val stringBuilder: StringBuilder = StringBuilder(fileContent)
    return parser.parse(stringBuilder) as JsonObject
}

fun getSearchCardMessage(
    messageContent: String,
    dataPath: String
): String {
    val searchContent = when {
        messageContent.startsWith("/searchcard") -> {
            messageContent.substringAfter("/searchcard").trim()
        }
        messageContent.startsWith("/sc") -> {
            messageContent.substringAfter("/sc").trim()
        }
        else -> {
            return "[INFO] 错误格式的命令。"
        }
    }

    val cardInfo = loadJson(dataPath, "cardInfo")
    val eggInfo = loadJson(dataPath, "eggInfo")
    val nickNameInfo = loadJson(dataPath, "nickNameInfo")

    if (searchContent in cardInfo) {
        return getCardInfo(cardInfo[searchContent] as JsonObject)
    }

    if (searchContent in eggInfo) {
        return eggInfo[searchContent] as String
    }

    val possibleAnswer: MutableList<String> = mutableListOf()

    for ((key, value) in cardInfo) {
        if (value is JsonObject) {
            if ((value["Name"] as String).contains(searchContent) ||
                (value["Info"] as String).contains(searchContent) ||
                (value["Flavor"] as String).contains(searchContent) ||
                (value["Categories"] as List<*>).joinToString().contains(searchContent)
            ) {
                possibleAnswer.add(key)
            }
        }
    }

    for ((key, value) in nickNameInfo) {
        if ((value as String).contains(searchContent)) {
            possibleAnswer.add(key)
        }
    }

    return when {
        possibleAnswer.size == 0 -> {
            "[INFO] 找不到！"
        }
        possibleAnswer.size == 1 -> {
            getCardInfo(cardInfo[possibleAnswer[0]] as JsonObject)
        }
        possibleAnswer.size >= 15 -> {
            "[INFO] 结果太多，有${possibleAnswer.size}个，请用更精确的关键词！"
        }
        else -> {
            getListCard(possibleAnswer, cardInfo)
        }
    }
}


fun getCardInfo(targetInfo: JsonObject): String {
    return """
        CardId: ${targetInfo["CardId"]}
        Name: ${targetInfo["Name"]}
        Strength: ${targetInfo["Strength"]}
        Info: ${targetInfo["Info"]}
        Group: ${targetInfo["Group"]}
        Faction: ${targetInfo["Faction"]}
        Categories: ${(targetInfo["Categories"] as List<*>).joinToString()}
        Story: ${targetInfo["Flavor"]}
    """.trimIndent()
}

fun getListCard(possibleAnswer: MutableList<String>, cardInfo: JsonObject): String {
    return possibleAnswer.joinToString { "${(cardInfo[it] as JsonObject)["Name"]}($it)" }
}

fun getSearchBaiduMessage(messageContent: String): String {
    val searchContent = when {
        messageContent.startsWith("/baidu") -> {
            messageContent.substringAfter("/baidu").trim()
        }
        messageContent.startsWith("/bd") -> {
            messageContent.substringAfter("/bd").trim()
        }
        else -> {
            return "[INFO] 错误格式的命令。"
        }
    }
    val encodedContent = java.net.URLEncoder.encode(searchContent, "utf-8")
    return "[INFO] 点击 http://www.baidu.com/s?wd=$encodedContent 查看搜索结果！"
}

fun getInfoMessage(messageContent: String, dataPath: String): String {
    val searchContent = when {
        messageContent.startsWith("/info") -> {
            messageContent.substringAfter("/info").trim()
        }
        else -> {
            return "[INFO] 错误格式的命令。"
        }
    }

    val info = loadJson(dataPath, "info")
    if (searchContent == "在线" || searchContent == "online") {
        return getOnlineMessage()
    }
    if (searchContent in info) {
        return info[searchContent] as String
    }

    return "[INFO] /info后面可以输入【文档】、【在线】、【匹配码】、【讨论】、【贴吧】或者【下载】查看相关内容！"
}

fun getOnlineMessage(): String {
    val extra = try {
        val text = URL("http://cynthia.ovyno.com:5005/api/gwentdata/onlinecount").readText()
        when {
            (text == "0") -> "现在DIY服没有人在线...QAQ\n"
            else -> "现在DIY服有${text}人在线！\n"
        }
    } catch (ex: Exception) {
        ""
    }

    return """[INFO] $extra DIY服的在线人数在：http://cynthia.ovyno.com:5005/
原服的在线人数在：http://cynthia.ovyno.com:5000/
欢迎大家一起打牌！"""
}

fun getAPIMessage(): String {
    val dateFormatter: DateFormat = SimpleDateFormat("yyyy-MM-dd")
    val today = dateFormatter.format(Date())
    val list = listOf<String>("queryranking", "queryenvironment", "querymatches", "querycard")
    val prefix = "http://cynthia.ovyno.com:5005/api/gwentdata/"
    return list.fold("[API]") { acc, string -> "$acc\n$prefix$string/$today" }
}

fun getHelpMessage(dataPath: String): String {
    val info = loadJson(dataPath, "info")
    return info["help"] as String
}

fun getWelcomeMessage(dataPath: String): String {
    val info = loadJson(dataPath, "info")
    return info["welcome"] as String
}