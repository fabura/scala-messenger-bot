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

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

object ValyaBotService extends LazyLogging {

  val dao = new InMemoryDao

  def handleMessage(me: model.FBMessageEventIn)(implicit ec: ExecutionContext, system: ActorSystem, materializer: ActorMaterializer): Unit = {
    val senderId = me.sender.id

    val (flowId, stepId) = dao.getStepId(senderId).getOrElse("basic" -> Steps.AskName)

    val stepsRunner = new InMemoryDao with StepsRunner with WithExecutors {
      implicit val ex = ec
      implicit val sys = system
    }

    val steps = Steps.find(flowId, stepId)
    if (steps.isEmpty) {
      logger.debug(s"Cannot find step for user: $senderId, $flowId, $stepId")
    } else {
      steps.get.map(s => stepsRunner.steps.getOrElse(s, sys.error(s"not step! $s"))).map(
        _.action).foreach(_.apply(me))
      val (newFlowId, newStepId) = Steps.nextStep(flowId, steps.get.last)
      logger.debug(s"next step: flowId = $newFlowId, stepId = $newStepId")
      dao.updateUserStepId(senderId, newFlowId, newStepId)
    }
    dao.print()
  }
}

trait BotStep {
  val id: StepId

  def action: FBMessageEventIn => Unit
}

case class Step(id: StepId, action: FBMessageEventIn => Unit) extends BotStep

trait WithExecutors {
  implicit def ex: ExecutionContext

  implicit def sys: ActorSystem
}

trait StepsRunner extends LazyLogging {
  this: WithExecutors with BotDao =>

  val steps: Map[StepId, Step] = Seq(
    Step(Steps.AskName, (me: FBMessageEventIn) => {
      val response = FBMessageEventOut(recipient = FBRecipient(me.sender.id),
        message = FBMessage(text = Some(s"Введите имя и фамилию")))
      HttpClient.post(response)
    }),
    Step(Steps.AddName, (me: FBMessageEventIn) => {
      val senderId = me.sender.id
      val updatedUser = getUser(senderId).flatMap(
        u => me.message.map(name => u.copy(name = name.text)))

      updatedUser.foreach(saveUser)
      if (updatedUser.isEmpty) logger.debug(s"User is empty! $senderId")
    }),
    Step(Steps.AskSkills, (me: FBMessageEventIn) => {
      HttpClient.post(FBMessageEventOut(recipient = FBRecipient(me.sender.id),
        message = FBMessage(text = Some(
          s"Пожалуйста, введите навыки. (Валенька, эта стадия еще не готова окончательно. Пока можно просто строкой писать навыки"))))
    }),
    Step(Steps.AddSkills, (me: FBMessageEventIn) => {
      val senderId = me.sender.id
      val updatedUser = getUser(senderId).flatMap(u => me.message.flatMap(
        message => message.text.map(
          text => u.copy(skills = text.split("\\s+").map(Skill.apply).toSeq))))
      updatedUser.foreach(saveUser)
      if (updatedUser.isEmpty) logger.debug(s"User is empty! $senderId")
    }),
    Step(Steps.AskSubskills, (me: FBMessageEventIn) => {
      HttpClient.post(FBMessageEventOut(recipient = FBRecipient(me.sender.id),
        message = FBMessage(text = Some(
          s"Пожалуйста, введите поднавыки. (Валенька, эта стадия еще не готова окончательно. Пока можно просто строкой писать навыки"))))
    }),
    Step(Steps.AddSubkills, (me: FBMessageEventIn) => {
      val senderId = me.sender.id
      val updatedUser = getUser(senderId).flatMap(u => me.message.flatMap(
        message => message.text.map(
          text => u.copy(subSkills = text.split("\\s+").map(SubSkill.apply).toSeq))))
      updatedUser.foreach(saveUser)
      if (updatedUser.isEmpty) logger.debug(s"User is empty! $senderId")
    }),
    Step(Steps.AskFrequency, (me: FBMessageEventIn) => {
      HttpClient.post(FBMessageEventOut(recipient = FBRecipient(me.sender.id), message = FBMessage(
        text = Some("Выберите периодичность"),
        quick_replies = Some(Seq(
          QuickReply(content_type = "text",
            title = "Раз в неделю",
            payload = "week"),
          QuickReply(content_type = "text",
            title = "Раз в 2 недели",
            payload = "2 weeks"),
          QuickReply(content_type = "text",
            title = "Раз в месяц",
            payload = "month"))))))
    }),
    Step(Steps.AddFrequency, (me: FBMessageEventIn) => {
      val senderId = me.sender.id
      val updatedUser = getUser(senderId).flatMap(
        u => me.postback.map(postback => u.copy(frequency = postback.payload)))
      updatedUser.foreach(saveUser)
      if (updatedUser.isEmpty) logger.debug(s"User is empty! $senderId")
    }),
    Step(Steps.Finish, (me: FBMessageEventIn) => {
      HttpClient.post(FBMessageEventOut(recipient = FBRecipient(me.sender.id),
        message = FBMessage(text = Some(s"Готово!"))))
    }),
    Step(Steps.Help, (me: FBMessageEventIn) => {
      HttpClient.post(FBMessageEventOut(recipient = FBRecipient(me.sender.id),
        message = FBMessage(text = Some(s"Тут будет помощь!"))))
    }),
  ).map(x => x.id -> x).toMap

}

object Steps extends LazyLogging {
  val AskName = StepId("ask name")
  val AddName = StepId("add name")
  val AskSkills = StepId("ask skills")
  val AddSkills = StepId("add skills")
  val AskSubskills = StepId("ask subskills")
  val AddSubkills = StepId("add subkills")
  val AskFrequency = StepId("ask frequency")
  val AddFrequency = StepId("add frequency")
  val Finish = StepId("finish")
  val Help = StepId("help")
  val flows: Map[String, List[List[StepId]]] = Map(
    "basic" -> ((AskName :: Nil) :: (AddName :: AskSkills :: Nil) :: (AddSkills :: AskSubskills :: Nil) :: (AddSubkills :: AskFrequency :: Nil) :: (AddFrequency :: Finish :: Nil) :: Nil),
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
