package com.cpuheater.bot

package object model {

  case class Button(`type`: String, title: String, payload: String)

  case class Payload(url: Option[String] = None, template_type: Option[String] = None, text: Option[String] = None, buttons: Option[Seq[Button]] = None)

  case class Attachment(`type`: String, payload: Payload)

  case class QuickReply(content_type: String, title: String, payload: String, image_url: Option[String] = None)

  case class FBMessage(
                        mid: Option[String] = None,
                        seq: Option[Long] = None,
                        text: Option[String] = None,
                        metadata: Option[String] = None,
                        attachment: Option[Attachment] = None,
                        quick_replies: Option[Seq[QuickReply]] = None
                      )

  case class FBPostback(title: Option[String], payload: Option[String])

  case class FBSender(id: String)

  case class FBRecipient(id: String)


  case class FBMessageEventIn(
                               sender: FBSender,
                               recipient: FBRecipient,
                               timestamp: Long,
                               message: Option[FBMessage],
                               postback: Option[FBPostback])

  case class FBMessageEventOut(
                                recipient: FBRecipient,
                                message: FBMessage)

  case class FBEntry(id: String, time: Long, messaging: List[FBMessageEventIn])

  case class FBPObject(`object`: String, entry: List[FBEntry])

  case class Skills(psid: String, skill: Seq[String])

}
