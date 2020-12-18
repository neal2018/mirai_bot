package com.example

import cn.hutool.http.HttpRequest
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.Parser
import kotlinx.coroutines.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.event.*
import net.mamoe.mirai.event.events.MemberJoinEvent
import net.mamoe.mirai.join
import net.mamoe.mirai.message.*
import net.mamoe.mirai.message.data.*
import java.io.File
import java.net.URL
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.*


suspend fun main() {
    val dataPath = System.getProperty("user.dir") + File.separator + "src/main/kotlin/com/example/cardData"
    val qqId = 2221744851L//Bot的QQ号，需为Long类型，在结尾处添加大写L
    val password = "*****"//Bot的密码
    val groups = listOf(945408322L)

    val subscribes = if (File("subs.json").exists()) {
        Klaxon().parseArray<Person>(File("subs.json").readText(Charsets.UTF_8)) as MutableList
    } else {
        mutableListOf<Person>()
    }

    val miraiBot = Bot(qqId, password) {
        fileBasedDeviceInfo("device.json") // 使用 "device.json" 保存设备信息
    }.alsoLogin()//新建Bot并登录
    miraiBot.subscribeMessages {
        (startsWith("/searchcard") or startsWith("/sc")) {
            reply(getSearchCardMessage(message.content, dataPath))
        }

        startsWith("/info") {
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

        startsWith("/addsub", removePrefix = true) {
            reply(addSubscription(subscribes, it))
        }

        startsWith("/unsub", removePrefix = true) {
            reply(unSubscription(subscribes, it))
        }

        startsWith("/showsub") {
            reply(showSubscription(subscribes))
        }

        (startsWith("/online") or startsWith("/ol")) {
            reply(getOnlineMessage())
        }

        (startsWith("/api") or startsWith("/Api") or startsWith("/API")) {
            reply(getAPIMessage())
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
    val nightDate: Date = nightCal.time
    Timer().scheduleAtFixedRate(object : TimerTask() {
        override fun run() {
            GlobalScope.launch { sendNightMessage(miraiBot, groups, dataPath) }
        }
    }, nightDate, 24 * 60 * 60 * 1000)

    Timer().scheduleAtFixedRate(object : TimerTask() {
        override fun run() {
            GlobalScope.launch { checkLiveStatus(miraiBot, subscribes, groups) }
        }
    }, Date(), 30 * 1000)

    miraiBot.join() // 等待 Bot 离线, 避免主线程退出
}

fun showSubscription(subscribes: MutableList<Person>): String {
    return if (subscribes.size == 0) {
        "当前没有订阅"
    } else {
        subscribes.fold("") { acc, person -> acc + person.username + "\n" }
    }
}

fun unSubscription(subscribes: MutableList<Person>, removeID: String): String {
    val userID = removeID.trim()
    val n = subscribes.size
    for (i in 0 until n) {
        if (subscribes[i].userID == userID) {
            val name = subscribes[i].username
            subscribes.removeAt(i)
            File("subs.json").writeText(Klaxon().toJsonString(subscribes))
            return "移除订阅 $name 成功"
        }
    }
    return "error"
}

data class Person(
    val username: String,
    val userID: String,
    val roomID: String,
    var liveStatus: Boolean
)

fun updateJson(dataPath: String) {
//    Runtime.getRuntime().exec(dataPath + File.separator + "update.sh")
}

fun addSubscription(subscribes: MutableList<Person>, newID: String): String {
    val userID = newID.trim()
    // check if is all number
    val pattern: Pattern = Pattern.compile("[0-9]*")
    val isNum: Matcher = pattern.matcher(newID)
    if (!isNum.matches()) {
        return "ID格式不正确"
    }
    for (person in subscribes) {
        if (person.userID == newID) {
            return "已经订阅过了"
        }
    }
    return try {
        val infoLink = "http://api.bilibili.com/x/space/acc/info?mid=$userID"
        val request = HttpRequest.get(infoLink).timeout(500)
            .header("User-Agent", "Bili live status checker")

        val response = request.executeAsync()
        val stringBuilder: StringBuilder = StringBuilder(response.body())
        val info = Parser.default().parse(stringBuilder) as JsonObject
        val infoData = info["data"] as JsonObject
        val name = infoData["name"] as String
        val roomID = (infoData["live_room"] as JsonObject)["roomid"].toString()
        subscribes.add(Person(name, userID, roomID, false))
        File("subs.json").writeText(Klaxon().toJsonString(subscribes))
        "订阅 $name 成功"
    } catch (ex: Exception) {
        "error"
    }
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
        bot.getGroup(group).sendMessage("这是今天的对局环境情况")
        bot.getGroup(group).sendMessage(getAPIMessage())
    }
}

suspend fun checkLiveStatus(bot: Bot, subscribes: List<Person>, groups: List<Long>) {
    for (person in subscribes) {
        val liveUrl = "http://api.live.bilibili.com/room/v1/Room/get_info?id="
        val request = HttpRequest.get(liveUrl + person.roomID).timeout(500)
            .header("User-Agent", "Bili live status checker")
        try {
            val response = request.executeAsync()
            val stringBuilder: StringBuilder = StringBuilder(response.body())
            val info = Parser.default().parse(stringBuilder) as JsonObject
            val infoData = info["data"] as JsonObject
            val liveStatus = infoData["live_status"] as Int
            if (liveStatus == 1 && !person.liveStatus) {
                person.liveStatus = true
                val liveRoom = "https://live.bilibili.com/" + person.roomID
                val title = infoData["title"] as String
                for (group in groups) {
                    bot.getGroup(group).sendMessage(person.username + "开播啦！！直播标题：\"$title\"，地址：$liveRoom")
                }
            } else if (liveStatus != 1) {
                person.liveStatus = false
            }
        } catch (ex: Exception) {

        }
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