package com.cpuheater.bot.json

import com.cpuheater.bot.model._
import spray.json.{DeserializationException, JsString, JsValue, JsonFormat, _}


object BotJson extends DefaultJsonProtocol {
  implicit val buttonFormat = jsonFormat3(Button)

  implicit val payloadFormat = jsonFormat4(Payload)

  implicit val attachmentFormat = jsonFormat2(Attachment)

  implicit val quickReplyFormat = jsonFormat4(QuickReply)

  implicit val fbMessageFormat = jsonFormat6(FBMessage)

  implicit val FBPostbackFormat = jsonFormat2(FBPostback)

  implicit val fbSenderFormat = jsonFormat1(FBSender)

  implicit val fbRecipientFormat = jsonFormat1(FBRecipient)

  implicit val fbMessageObjectFormat = jsonFormat5(FBMessageEventIn)

  implicit val fbMessageEventOutFormatOut = jsonFormat2(FBMessageEventOut)

  implicit val fbEntryFormat = jsonFormat3(FBEntry)

  implicit val fbPObjectFormat = jsonFormat2(FBPObject)

  implicit val skillsFormat = jsonFormat2(Skills)

}
