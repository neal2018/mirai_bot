package com.example

import cn.hutool.http.HttpRequest
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.Parser
import kotlinx.coroutines.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.utils.BotConfiguration.MiraiProtocol.ANDROID_PAD
import net.mamoe.mirai.event.events.MemberJoinEvent
import net.mamoe.mirai.event.subscribeGroupMessages
import net.mamoe.mirai.event.subscribeMessages
import java.io.File
import java.net.URL
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.*

const val subListFile = "subs_list.json"
const val steamListFile = "steam_list.json"
const val groupFile = "groups.txt"
const val groupNotifyFile = "groups_notify.txt"
val dataPath = System.getProperty("user.dir") + File.separator + "src/main/kotlin/com/example/cardData"
val groupsNotify = File(groupNotifyFile).useLines { it.toList() }.map { it.toLong() }
val groups = File(groupFile).useLines { it.toList() }.map { it.toLong() }

@DelicateCoroutinesApi
suspend fun main() {
    val qqId = 2221744851L // Bot的QQ号，需为Long类型，在结尾处添加大写L
    val password = readLine()!!
    var pastNamoTime = Date()
    for (g in groups) {
        println(g)
    }
    val subscribes = if (File(subListFile).exists()) {
        Klaxon().parse<MutableMap<String, MutableList<JsonObject>>>(File(subListFile).readText(Charsets.UTF_8))!!
            .mapValues { (_, v) ->
                v.map { x -> Klaxon().parseFromJsonObject<Person>(x)!! }.toMutableList()
            }.toMutableMap()
    } else {
        mutableMapOf()
    }

    val steamSubscribes = if (File(steamListFile).exists()) {
        Klaxon().parse<MutableMap<String, MutableList<JsonObject>>>(File(steamListFile).readText(Charsets.UTF_8))!!
            .mapValues { (_, v) ->
                v.map { x -> Klaxon().parseFromJsonObject<SteamItem>(x)!! }.toMutableList()
            }.toMutableMap()
    } else {
        mutableMapOf()
    }

    val miraiBot = BotFactory.newBot(qqId, password) {
        fileBasedDeviceInfo("device.json") // 使用 device.json 存储设备信息
        protocol = ANDROID_PAD // 切换协议
    }.alsoLogin()

    GlobalEventChannel.subscribeMessages {
        (startsWith("/searchcard") or startsWith("/sc")) reply { cmd ->
            getSearchCardMessage(cmd, dataPath)
        }
        (startsWith("/baidu") or startsWith("/bd")) reply { cmd ->
            getSearchBaiduMessage(cmd)
        }
        startsWith("/smsearch") reply { cmd ->
            getSteamDBSearch(cmd)
        }
        startsWith("/swdeck") reply {
            showDeck()
        }
    }

    GlobalEventChannel.subscribeAlways<MemberJoinEvent> { event ->
        event.group.sendMessage(getWelcomeMessage(dataPath, this.group.id.toString()))
    }

    GlobalEventChannel.subscribeGroupMessages {
        (startsWith("/welcome") or startsWith("/w")) reply {
            if (this.group.id in groups) getWelcomeMessage(dataPath, this.group.id.toString()) else Unit
        }
        startsWith("/swelcome") reply { cmd ->
            if (this.group.id in groups) setWelcomeMessage(dataPath, this.group.id.toString(), cmd) else Unit
        }
        ((startsWith("/help") or startsWith("/h"))) reply {
            if (this.group.id in groups) getHelpMessage(dataPath) else Unit
        }
        startsWith("/info") reply { cmd ->
            if (this.group.id in groups) getInfoMessage(cmd, dataPath) else Unit
        }
        (startsWith("/online") or startsWith("/ol")) reply {
            if (this.group.id in groups) getOnlineMessage() else Unit
        }
        (startsWith("/api") or startsWith("/Api") or startsWith("/API")) reply {
            if (this.group.id in groups) getAPIMessage() else Unit
        }
        (startsWith("/searchdeck") or startsWith("/sd")) reply { cmd ->
            if (this.group.id in groups) getDeck(cmd) else Unit
        }
        (startsWith("/adddeck") or startsWith("/ad")) reply { cmd ->
            if (this.group.id in groups) addDeck(cmd) else Unit
        }
        startsWith("/bilisub") reply { cmd ->
            addSubscription(subscribes, this.group.id.toString(), cmd)
        }
        startsWith("/biliunsub") reply { cmd ->
            unSubscription(subscribes, this.group.id.toString(), cmd)
        }
        startsWith("/bilishow") reply {
            showSubscription(subscribes, this.group.id.toString())
        }
        startsWith("/smsub") reply { cmd ->
            addSteamSubscription(steamSubscribes, this.group.id.toString(), cmd)
        }
        startsWith("/smunsub") reply { cmd ->
            unSteamSubscription(steamSubscribes, this.group.id.toString(), cmd)
        }
        startsWith("/smshow") reply {
            showSteamSubscription(steamSubscribes, this.group.id.toString())
        }
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
    }, morningDate, 86400000L)

    val nightCal = Calendar.getInstance()
    nightCal[Calendar.HOUR_OF_DAY] = 23
    nightCal[Calendar.MINUTE] = 50
    nightCal[Calendar.SECOND] = 0
    val nightDate: Date = nightCal.time
    Timer().scheduleAtFixedRate(object : TimerTask() {
        override fun run() {
            GlobalScope.launch { sendNightMessage(miraiBot, groups, dataPath) }
        }
    }, nightDate, 86400000L)

    Timer().scheduleAtFixedRate(object : TimerTask() {
        override fun run() {
            GlobalScope.launch { checkLiveStatus(miraiBot, subscribes) }
        }
    }, Date(), 60000L)

    Timer().scheduleAtFixedRate(object : TimerTask() {
        override fun run() {
            GlobalScope.launch { checkSteamLiveStatus(miraiBot, steamSubscribes) }
        }
    }, Date(), 300000L)

    miraiBot.join() // 等待 Bot 离线, 避免主线程退出
}

data class Person(
    val username: String,
    val userID: String,
    val roomID: String,
    var liveStatus: Boolean
)

data class SteamItem(
    val itemName: String,
    val appID: String,
    var price: String,
    var discount_percent: Int
)

fun getSteamDBSearch(cmd: String): String {
    val itemID = when {
        cmd.startsWith("/smsearch") -> {
            cmd.substringAfter("/smsearch").trim()
        }
        else -> {
            return "[INFO] 错误格式的命令。"
        }
    }
    val encodedContent = java.net.URLEncoder.encode(itemID, "utf-8")
    return "https://steamdb.info/search/?a=app&q=$encodedContent"
}

fun showSteamSubscription(steamSubscribes: MutableMap<String, MutableList<SteamItem>>, group: String): String {
    val subscribesList = steamSubscribes.getOrPut(group) { mutableListOf() }
    return if (subscribesList.size == 0) {
        "当前没有订阅"
    } else {
        subscribesList.fold("") { acc, item -> "$acc ${item.itemName} (${item.appID})，现价：${item.price}，折扣：${item.discount_percent}% \n" }
    }
}

fun unSteamSubscription(
    steamSubscribes: MutableMap<String, MutableList<SteamItem>>,
    group: String,
    removeID: String
): String {
    val itemID = when {
        removeID.startsWith("/smunsub") -> {
            removeID.substringAfter("/smunsub").trim()
        }
        else -> {
            return "[INFO] 错误格式的命令。"
        }
    }
    val subscribesList = steamSubscribes.getOrPut(group) { mutableListOf() }
    for (i in 0 until subscribesList.size) {
        if (subscribesList[i].appID == itemID) {
            val name = subscribesList[i].itemName
            subscribesList.removeAt(i)
            File(steamListFile).writeText(Klaxon().toJsonString(steamSubscribes))
            return "移除订阅 $name 成功"
        }
    }
    return "error"
}

fun addSteamSubscription(
    steamSubscribes: MutableMap<String, MutableList<SteamItem>>,
    group: String,
    newID: String
): String {
    val itemID = when {
        newID.startsWith("/smsub") -> {
            newID.substringAfter("/smsub").trim()
        }
        else -> {
            return "[INFO] 错误格式的命令。"
        }
    }
    if (!Pattern.compile("[0-9]*").matcher(itemID).matches()) {
        return "ID格式不正确"
    }
    val subscribesList = steamSubscribes.getOrPut(group) { mutableListOf() }
    for (item in subscribesList) {
        if (item.appID == itemID) {
            return "已经订阅过了"
        }
    }
    return try {
        val infoLink = "https://store.steampowered.com/api/appdetails?appids=$itemID&cc=cn&filters=basic"
        val request = HttpRequest.get(infoLink).timeout(2500).header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36"
        )
        val response = request.executeAsync()
        val stringBuilder: StringBuilder = StringBuilder(response.body())
        val info = Parser.default().parse(stringBuilder) as JsonObject
        val infoData = (info[itemID] as JsonObject)["data"] as JsonObject
        val name = infoData["name"] as String
        subscribesList.add(SteamItem(name, itemID, "NO DATA", 0))
        File(steamListFile).writeText(Klaxon().toJsonString(steamSubscribes))
        "订阅 $name 成功"
    } catch (ex: Exception) {
        "网络状况不良，请稍等再试..."
    }
}

fun showSubscription(subscribes: MutableMap<String, MutableList<Person>>, group: String): String {
    val subscribesList = subscribes.getOrPut(group) { mutableListOf() }
    return if (subscribesList.size == 0) {
        "当前没有订阅"
    } else {
        subscribesList.fold("") { acc, person -> "$acc ${person.username} (${person.userID}) \n" }
    }
}

fun unSubscription(subscribes: MutableMap<String, MutableList<Person>>, group: String, removeID: String): String {
    val userID = when {
        removeID.startsWith("/biliunsub") -> {
            removeID.substringAfter("/biliunsub").trim()
        }
        else -> {
            return "[INFO] 错误格式的命令。"
        }
    }
    val subscribesList = subscribes.getOrPut(group) { mutableListOf() }
    for (i in 0 until subscribesList.size) {
        if (subscribesList[i].userID == userID) {
            val name = subscribesList[i].username
            subscribesList.removeAt(i)
            File(subListFile).writeText(Klaxon().toJsonString(subscribes))
            return "移除订阅 $name 成功"
        }
    }
    return "网络状况不良，请稍等再试.。。"
}

//fun updateJson(dataPath: String) {
////    Runtime.getRuntime().exec(dataPath + File.separator + "update.sh")
//}

fun addSubscription(subscribes: MutableMap<String, MutableList<Person>>, group: String, newID: String): String {
    val userID = when {
        newID.startsWith("/bilisub") -> {
            newID.substringAfter("/bilisub").trim()
        }
        else -> {
            return "[INFO] 错误格式的命令。"
        }
    }
    // check if is all number
    if (!Pattern.compile("[0-9]*").matcher(userID).matches()) {
        return "BILI UID格式不正确"
    }

    val subscribesList = subscribes.getOrPut(group) { mutableListOf() }
    for (person in subscribesList) {
        if (person.userID == userID) {
            return "已经订阅过了"
        }
    }
    return try {
        val infoLink = "https://api.bilibili.com/x/space/acc/info?mid=$userID"
        val request = HttpRequest.get(infoLink).timeout(500)
            .header("User-Agent", "Bili live status checker")

        val response = request.executeAsync()
        val stringBuilder: StringBuilder = StringBuilder(response.body())
        val info = Parser.default().parse(stringBuilder) as JsonObject
        val infoData = info["data"] as JsonObject
        val name = infoData["name"] as String
        val roomID = (infoData["live_room"] as JsonObject)["roomid"].toString()
        subscribesList.add(Person(name, userID, roomID, false))
        File(subListFile).writeText(Klaxon().toJsonString(subscribes))
        "订阅 $name 成功"
    } catch (ex: Exception) {
        "网络状况不良，请稍等再试...."
    }
}

suspend fun sendMorningMessage(bot: Bot, groups: List<Long>, dataPath: String) {
    val info = loadJson(dataPath, "info")
    for (group in groupsNotify) {
        bot.getGroup(group)?.sendMessage(info["morning"] as String)
    }
}

suspend fun sendNightMessage(bot: Bot, groups: List<Long>, dataPath: String) {
    val info = loadJson(dataPath, "info")
    for (group in groupsNotify) {
        bot.getGroup(group)?.sendMessage(info["night"] as String)
        bot.getGroup(group)?.sendMessage("这是今天的对局环境情况")
        bot.getGroup(group)?.sendMessage(getAPIMessage())
    }
}

suspend fun checkLiveStatus(bot: Bot, subscribes: MutableMap<String, MutableList<Person>>) {
    subscribes.forEach { (group, subscribesList) ->
        for (person in subscribesList) {
            val liveUrl = "https://api.live.bilibili.com/room/v1/Room/get_info?id="
            val request = HttpRequest.get(liveUrl + person.roomID).timeout(1000)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36"
                )
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
                    bot.getGroup(group.toLong())?.sendMessage(person.username + " 开播啦！！直播标题：\"$title\"，地址：$liveRoom")
                } else if (liveStatus != 1) {
                    person.liveStatus = false
                }
                File(subListFile).writeText(Klaxon().toJsonString(subscribes))
            } catch (ex: Exception) {

            }
        }
    }

}

suspend fun checkSteamLiveStatus(
    bot: Bot,
    steamSubscribes: MutableMap<String, MutableList<SteamItem>>
) {
    steamSubscribes.forEach { (group, subscribesList) ->
        if (subscribesList.isNotEmpty()) {
            val queryList = subscribesList.joinToString(separator = ",") { it.appID }
            val queryURL =
                "https://store.steampowered.com/api/appdetails?appids=${queryList}&cc=cn&filters=price_overview"
            println(queryURL)
            val request = HttpRequest.get(queryURL).timeout(2500)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36"
                )
            try {
                val response = request.executeAsync()
                val stringBuilder: StringBuilder = StringBuilder(response.body())
                val info = Parser.default().parse(stringBuilder) as JsonObject
                for (item in subscribesList) {
                    try {
                        val infoData = (info[item.appID] as JsonObject)["data"] as JsonObject
                        val priceOverview = infoData["price_overview"] as JsonObject
                        val discountPercent = priceOverview["discount_percent"] as Int
                        val price = priceOverview["final_formatted"] as String
                        item.price = price
                        if (discountPercent > item.discount_percent) {
                            item.discount_percent = discountPercent
                            val steamDB = "https://steamdb.info/app/${item.appID}/"
                            val steam = "https://store.steampowered.com/app/${item.appID}/"
                            bot.getGroup(group.toLong())
                                ?.sendMessage(item.itemName + " 目前折扣：$discountPercent%，现价：$price。地址：steamDB：$steamDB, steam: $steam")
                        } else {
                            item.discount_percent = discountPercent
                        }
                    } catch (ex: Exception) {
                        println(ex)
                    }
                    File(steamListFile).writeText(Klaxon().toJsonString(steamSubscribes))
                }
            } catch (ex: Exception) {
                println(ex)
            }
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
    val searchContentList = searchContent.split(" ").filter { x -> x.isNotEmpty() }

    fun checkContainsAll(toSearch: String): Boolean {
        for (keyword in searchContentList) {
            if (!toSearch.contains(keyword, true)) return false
        }
        return true
    }

    val possibleAnswer: MutableList<String> = mutableListOf()
    for ((key, value) in cardInfo) {
        if (value is JsonObject) {
            if (checkContainsAll(value["Name"] as String) ||
                checkContainsAll(value["Info"] as String) ||
                checkContainsAll(value["Flavor"] as String) ||
                checkContainsAll((value["Categories"] as List<*>).joinToString())
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
    return "https://www.baidu.com/s?wd=$encodedContent"
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
        val text = URL("http://106.15.38.165:5005/api/gwentdata/onlinecount").readText()
        when {
            (text == "0") -> "现在DIY服没有人在线...QAQ\n"
            else -> "现在DIY服有${text}人在线！\n"
        }
    } catch (ex: Exception) {
        ""
    }

    return """[INFO] $extra DIY服的在线人数在：http://106.15.38.165:5005/
原服的在线人数在：http://106.15.38.165:5000/
欢迎大家一起打牌！"""
}

fun getAPIMessage(): String {
    val dateFormatter: DateFormat = SimpleDateFormat("yyyy-MM-dd")
    val today = dateFormatter.format(Date())
    val list = listOf("queryranking", "queryenvironment", "querymatches", "querycard")
    val prefix = "http://106.15.38.165:5005/api/gwentdata/"
    return list.fold("[API]") { acc, string -> "$acc\n$prefix$string/$today" }
}

fun getHelpMessage(dataPath: String): String {
    val info = loadJson(dataPath, "info")
    return info["help"] as String
}

fun getWelcomeMessage(dataPath: String, id: String): String {
    val info = loadJson(dataPath, "info")
    return if ("welcome$id" in info) {
        info["welcome$id"] as String
    } else {
        ""
    }
}

fun setWelcomeMessage(dataPath: String, id: String, cmd: String): String {
    val msg = when {
        cmd.startsWith("/swelcome") -> {
            cmd.substringAfter("/swelcome").trim()
        }
        else -> {
            return "[INFO] 错误格式的命令。"
        }
    }
    val infoPath = dataPath + File.separator + "info.json"
    val info = Klaxon().parse<MutableMap<String, String>>(File(infoPath).readText(Charsets.UTF_8))!!
    info["welcome$id"] = msg
    File(infoPath).writeText(Klaxon().toJsonString(info))
    return "设置欢迎信息成功！"
}

fun getDeck(messageContent: String): String {
    val searchContent = when {
        messageContent.startsWith("/searchdeck") -> {
            messageContent.substringAfter("/searchdeck").trim()
        }
        messageContent.startsWith("/sd") -> {
            messageContent.substringAfter("/sd").trim()
        }
        else -> {
            return "[INFO] 错误格式的命令。"
        }
    }

    val deckInfo = loadJson(dataPath, "deckInfo")
    if (searchContent in deckInfo) {
        return (deckInfo[searchContent] as String).split("###").toTypedArray()
            .fold("[INFO]$searchContent\n") { acc, code -> "$acc $code \n" }
    }

    val possibleAnswer: MutableList<String> = mutableListOf()

    for ((key, _) in deckInfo) {
        if (key.contains(searchContent)) {
            possibleAnswer.add(key)
        }
    }

    return when {
        possibleAnswer.size == 0 -> {
            "[INFO] 找不到！"
        }
        possibleAnswer.size == 1 -> {
            (deckInfo[possibleAnswer[0]] as String).split("###").toTypedArray()
                .fold("[INFO]${possibleAnswer[0]}\n") { acc, code -> "$acc $code \n" }
        }
        possibleAnswer.size >= 15 -> {
            "[INFO] 结果太多，有${possibleAnswer.size}个，请用更精确的关键词！"
        }
        else -> {
            possibleAnswer.fold("[INFO]\n") { acc, code -> "$acc $code \n" }
        }
    }
}


fun addDeck(messageContent: String): String {
    val searchContent = when {
        messageContent.startsWith("/adddeck") -> {
            messageContent.substringAfter("/adddeck").trim()
        }
        messageContent.startsWith("/ad") -> {
            messageContent.substringAfter("/ad").trim()
        }
        else -> {
            return "[INFO] 错误格式的命令。"
        }
    }
    val inputs = searchContent.split(" ").toTypedArray()
    println(inputs)
    if (inputs.size != 2) return "[INFO] 错误格式的命令。"

    val deckInfo = loadJson(dataPath, "deckInfo")
    val deckInfoPath = dataPath + File.separator + "deckInfo.json"
    if (inputs[0] in deckInfo) {
        val current = (deckInfo[inputs[0]] as String).split("###").toTypedArray()
        if (!Arrays.stream(current).anyMatch { t -> t == inputs[1] })
            deckInfo[inputs[0]] = (deckInfo[inputs[0]] as String) + "###" + inputs[1]
        else {
            return "[INFO] 已存在的卡组码！"
        }
    } else {
        deckInfo[inputs[0]] = inputs[1]
    }
    File(deckInfoPath).writeText(Klaxon().toJsonString(deckInfo))
    return getDeck("/sd" + inputs[0])
}

fun showDeck(): String {
    val deckInfo = loadJson(dataPath, "deckInfo")
    var res = "[INFO] "
    for ((key, _) in deckInfo) {
        res += "$key "
    }
    return res
}