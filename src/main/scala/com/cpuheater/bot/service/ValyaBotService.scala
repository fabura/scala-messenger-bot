package com.cpuheater.bot.service

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.cpuheater.bot.db.{BotDao, InMemoryDao}
import com.cpuheater.bot.db.DbModel.{Skill, StepId, SubSkill}
import com.cpuheater.bot.model
import com.cpuheater.bot.model.{Attachment, Button, FBMessage, FBMessageEventIn, FBMessageEventOut, FBPostback, FBRecipient, Payload, QuickReply}
import com.cpuheater.bot.util.HttpClient
import com.cpuheater.bot.json.BotJson._
import com.cpuheater.bot.service.Steps.logger
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

object ValyaBotService extends LazyLogging {

    val dao_ = new InMemoryDao

  def handleMessage(me: model.FBMessageEventIn)(implicit ec: ExecutionContext, system: ActorSystem, materializer: ActorMaterializer): Unit = {
    val senderId = me.sender.id

    val stepsRunner = new StepsRunner with WithExecutors with WithDao {
      val dao = dao_
      implicit val ex = ec
      implicit val sys = system
    }

    val (flowId, stepId) = stepsRunner.dao.getStepId(senderId).getOrElse("basic" -> Steps.AskName)

    val steps = Steps.find(flowId, stepId)
    if (steps.isEmpty) {
      logger.debug(s"Cannot find step for user: $senderId, $flowId, $stepId")
      Future(())
    } else {
      Future.sequence(
        steps.get.map(s => stepsRunner.steps.getOrElse(s, sys.error(s"not step! $s"))).map(
          _.action).map(_.apply(me))
      ).map {
        _ =>
          val (newFlowId, newStepId) = Steps.nextStep(flowId, steps.get.last)
          logger.debug(s"next step: flowId = $newFlowId, stepId = $newStepId")
          Future(stepsRunner.dao.updateUserStepId(senderId, newFlowId, newStepId))
      }
    }.map(_ => stepsRunner.dao.print())
  }
}

trait BotStep {
  val id: StepId

  def action: FBMessageEventIn => Future[Unit]
}

case class Step(id: StepId, action: FBMessageEventIn => Future[Unit]) extends BotStep

trait WithExecutors {
  implicit def ex: ExecutionContext

  implicit def sys: ActorSystem
}

trait WithDao {
  def dao: BotDao
}

trait StepsRunner extends LazyLogging {
  this: WithExecutors with WithDao =>

  val steps: Map[StepId, Step] = Seq(
    Step(Steps.AskName, (me: FBMessageEventIn) => {
      val response = FBMessageEventOut(recipient = FBRecipient(me.sender.id),
        message = FBMessage(text = Some(s"""
          Your next answers will be visible to your potential colleagues so that they can know you better 🤝
          You can change your answers any time later.
          """)))
      HttpClient.post(response)
      val response2 = FBMessageEventOut(recipient = FBRecipient(me.sender.id),
        message = FBMessage(text = Some(s"""Please describe yourself in 2-5 sentences.
Example: Im Ashley. I work in a bank. In my free time I learn data science and photograph events. Huge animals fan 🦎
          """)))
      HttpClient.post(response2)
    }),
    Step(Steps.AddName, (me: FBMessageEventIn) => Future {
      val senderId = me.sender.id
      val updatedUser = dao.getUser(senderId).flatMap(
        u => me.message.map(name => u.copy(name = name.text)))

      updatedUser.foreach(dao.saveUser)
      if (updatedUser.isEmpty) logger.debug(s"User is empty! $senderId")
    }),
    Step(Steps.AskSkills, (me: FBMessageEventIn) => {
      HttpClient.post(FBMessageEventOut(recipient = FBRecipient(me.sender.id),
        message = FBMessage(attachment =
          Some(Attachment(`type` = "template",
            payload = Payload(
              template_type = Some("button"),
              text = Some("Nice to meet you!\nWhat kind of work you can do?"),
              buttons = Some(Seq(
                Button(
                  `type` = "web_url",
                  title = "Press here!",
                  url = Some(s"https://secure-cove-14093.herokuapp.com/skills.html?psid=${me.sender.id}"),
                  webview_height_ratio = Some("full"),
                  messenger_extensions = Some("true")
                )))))))))
    }),
    Step(Steps.AddSkills, (me: FBMessageEventIn) => Future {
      val senderId = me.sender.id
      val updatedUser = dao.getUser(senderId).flatMap(u => me.message.flatMap(
        message => message.text.map(
          text => u.copy(skills = text.split("\\s+").map(Skill.apply).toSeq))))
      updatedUser.foreach(dao.saveUser)
      if (updatedUser.isEmpty) logger.debug(s"User is empty! $senderId")
    }),
    //    Step(Steps.AskSubskills, (me: FBMessageEventIn) => {
    //      HttpClient.post(FBMessageEventOut(recipient = FBRecipient(me.sender.id),
    //        message = FBMessage(text = Some(
    //          s"Пожалуйста, введите поднавыки. (Валенька, эта стадия еще не готова окончательно. Пока можно просто строкой писать навыки"))))
    //    }),
    //    Step(Steps.AddSubkills, (me: FBMessageEventIn) => {
    //      val senderId = me.sender.id
    //      val updatedUser = getUser(senderId).flatMap(u => me.message.flatMap(
    //        message => message.text.map(
    //          text => u.copy(subSkills = text.split("\\s+").map(SubSkill.apply).toSeq))))
    //      updatedUser.foreach(saveUser)
    //      if (updatedUser.isEmpty) logger.debug(s"User is empty! $senderId")
    //    }),
    Step(Steps.AskFrequency, (me: FBMessageEventIn) => {
      Asker.askFrequency(me.sender.id)
    }),
    Step(Steps.AddFrequency, (me: FBMessageEventIn) => Future {
      val senderId = me.sender.id
      val updatedUser = dao.getUser(senderId).flatMap(
        u => me.postback.map(postback => u.copy(frequency = postback.payload)))
      updatedUser.foreach(dao.saveUser)
      if (updatedUser.isEmpty) logger.debug(s"User is empty! $senderId")
    }),
    Step(Steps.Finish, (me: FBMessageEventIn) => {
     Asker.ready(me.sender.id)
    }),
    Step(Steps.Help, (me: FBMessageEventIn) => {
      HttpClient.post(FBMessageEventOut(recipient = FBRecipient(me.sender.id),
        message = FBMessage(text = Some(s"There will be help!"))))
    }),
  ).map(x => x.id -> x).toMap

}

object Asker {
  def ready(psid: String)(implicit ec: ExecutionContext, system: ActorSystem) = {
    HttpClient.post(FBMessageEventOut(recipient = FBRecipient(psid),
      message = FBMessage(text = Some(s"""Well done! 🌟
                                         |✔️You will receive task options regularly.
                                         |✔️If you don't want or don't feel ready to do the suggested task, we will show you another available option.
                                         |✔️Once you accept the task, we will connect you with the client to discuss the details.
                                         |✔️If anything goes wrong, please tell us.
                                         |""".stripMargin))))
    HttpClient.post(FBMessageEventOut(recipient = FBRecipient(psid),
      message = FBMessage(text = Some(s"""We will send you task options as soon as they appear⏳
                                         |Have a productive day!
                                         |""".stripMargin))))

  }

  def askFrequency(psid: String)(implicit ec: ExecutionContext, system: ActorSystem) = {
    HttpClient.post(FBMessageEventOut(recipient = FBRecipient(psid), message = FBMessage(
      text = Some("We will send you task options as they appear. How often do you want to receive them? \uD83D\uDCC5 "),
      quick_replies = Some(Seq(
        QuickReply(content_type = "text",
          title = "Once a week",
          payload = "week"),
        QuickReply(content_type = "text",
          title = "Every 2 weeks",
          payload = "2 weeks"),
        QuickReply(content_type = "text",
          title = "Once a month",
          payload = "month"))))))


  }

}

object Steps extends LazyLogging {
  val AskName = StepId("ask name")
  val AddName = StepId("add name")
  val AskSkills = StepId("ask skills")
  val AddSkills = StepId("add skills")
  //  val AskSubskills = StepId("ask subskills")
  //  val AddSubkills = StepId("add subkills")
  val AskFrequency = StepId("ask frequency")
  val AddFrequency = StepId("add frequency")
  val Finish = StepId("finish")
  val Help = StepId("help")
  val flows: Map[String, List[List[StepId]]] = Map(
    "basic" -> ((AskName :: Nil) :: (AddName :: AskSkills :: Nil) :: (AddSkills :: /*AskSubskills ::*/ Nil) :: (/*AddSubkills :: */ AskFrequency :: Nil) :: (AddFrequency :: Finish :: Nil) :: Nil),
    "help" -> ((Help :: Nil) :: Nil)
  )

  def nextStep(flowId: String, stepId: StepId): (String, StepId) = {
    val nextStep = flows.get(flowId).flatMap(
      _.dropWhile(x => !x.contains(stepId)).drop(1).headOption)
    nextStep.map(flowId -> _.head).getOrElse("help" -> Help)
  }

  def find(flowId: String, stepId: StepId): Option[List[StepId]] = {
    flows.get(flowId)
      .flatMap(_.find(_.contains(stepId)))
      .map(_.dropWhile(_ != stepId))
  }
}
