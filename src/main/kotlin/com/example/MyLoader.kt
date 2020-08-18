package com.example

import net.mamoe.mirai.Bot
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.join
import net.mamoe.mirai.event.subscribeMessages
import net.mamoe.mirai.event.events.MemberJoinEvent
import net.mamoe.mirai.event.subscribeAlways
import net.mamoe.mirai.message.data.content
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import java.io.File

suspend fun main() {
    val dataPath = System.getProperty("user.dir") + File.separator + "src/main/kotlin/com/example/cardData"

    val cardInfo = loadJson(dataPath, "cardInfo")
    val eggInfo = loadJson(dataPath, "eggInfo")
    val nickNameInfo = loadJson(dataPath, "nickNameInfo")

    val qqId = 2221744851L//Bot的QQ号，需为Long类型，在结尾处添加大写L
    val password = "********"//Bot的密码
    val miraiBot = Bot(qqId, password).alsoLogin()//新建Bot并登录
    miraiBot.subscribeMessages {
        (startsWith("/searchcard") or startsWith("/sc")) {
            reply(getSearchCardMessage(message.content, cardInfo, eggInfo, nickNameInfo))
        }

        (startsWith("/info")) {
            reply(getInfoMessage(message.content))
        }

        (startsWith("/help") or startsWith("/h")) {
            reply(getHelpMessage())
        }

        (startsWith("/welcome") or startsWith("/w")) {
            reply(getWelcomeMessage())
        }

        (startsWith("/baidu") or startsWith("/bd")) {
            reply(getSearchBaiduMessage(message.content))
        }
    }

    miraiBot.subscribeAlways<MemberJoinEvent> {
        it.group.sendMessage(getWelcomeMessage())
    }

    miraiBot.join() // 等待 Bot 离线, 避免主线程退出
}


fun loadJson(dataPath: String, name: String): JsonObject {
    val fileContent = File(dataPath + File.separator + name + ".json").readText(Charsets.UTF_8)

    val parser: Parser = Parser.default()
    val stringBuilder: StringBuilder = StringBuilder(fileContent)
    return parser.parse(stringBuilder) as JsonObject
}

fun getSearchCardMessage(
    messageContent: String,
    cardInfo: JsonObject,
    eggInfo: JsonObject,
    nickNameInfo: JsonObject
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

fun getInfoMessage(messageContent: String): String {
    val searchContent = when {
        messageContent.startsWith("/info") -> {
            messageContent.substringAfter("/info").trim()
        }
        else -> {
            return "[INFO] 错误格式的命令。"
        }
    }

    return if (searchContent.contains("文档")) {
        """
        [INFO] DIY服务器更新内容在：https://shimo.im/docs/TQdjjwpPwd9hJhKc
        DIY的修改意见在：https://shimo.im/docs/hRIn0C91IFUYZZ6n
        讨论区在：https://github.com/LegacyGwent/LegacyGwent/issues
        欢迎大家踊跃参与！
    """.trimIndent()
    } else if (searchContent.contains("在线") || searchContent.contains("online")) {
        """
        [INFO] DIY服的在线人数在：http://cynthia.ovyno.com:5005/
        原服的在线人数在：http://cynthia.ovyno.com:5000/
        欢迎大家一起打牌！
    """.trimIndent()
    } else if (searchContent.contains("下载")) {
        """
        [INFO] 原版服务器下载地址：群文件/客户端
        DIY服下载地址：http://cynthia.ovyno.com:5005/download
        zip结尾的是电脑版，apk结尾的是安卓版，dmg结尾的是mac版
        小助手欢迎大家下载！
    """.trimIndent()
    } else if (searchContent.contains("讨论")) {
        "[INFO] https://github.com/LegacyGwent/LegacyGwent/issues"
    } else if (searchContent.contains("贴吧")) {
        "[INFO] https://tieba.baidu.com/f?ie=utf-8&kw=%E6%80%80%E6%97%A7%E6%98%86%E7%89%B9%E7%89%8C"
    } else if (searchContent.contains("匹配码")) {
        """
        [INFO] 匹配界面右下角可以输入匹配码，匹配码一样的玩家可以相互好友对战！
        匹配码输入ai或者ai1可以匹配ai，但会优先匹配在线玩家。使用ai#f或者ai1#f可以强制匹配ai。
    """.trimIndent()
    } else if (searchContent.contains("repo")) {
        "[INFO] 小助手开源在：https://github.com/neal2018/mirai_bot"
    } else {
        "[INFO] /info后面可以输入【文档】、【在线】、【匹配码】、【讨论】、【贴吧】或者【下载】查看相关内容！"
    }
}

fun getHelpMessage(): String {
    return """[HELP] 发送以下命令可以触发相应效果:
/help 或 /h: 显示本信息
/info: 显示相关的信息
/welcome: 发送入群欢迎信息
/searchcard 或 /sc + 关键词: 搜索卡牌效果
/baidu 或 /bd + 关键词: 使用百度搜索
小助手期待大家多多打牌！"""
}

fun getWelcomeMessage(): String {
    return """[WELCOME] 新人好！群里主要讨论老昆特相关事宜！
原版服务器下载地址: 群文件/客户端
DIY服下载地址: http://cynthia.ovyno.com:5005/download
zip结尾的是电脑版，apk结尾的是安卓版，dmg结尾的是mac版
DIY的修改意见在: https://shimo.im/docs/hRIn0C91IFUYZZ6n
讨论区在: https://github.com/LegacyGwent/LegacyGwent/issues
期待你的参与！"""
}