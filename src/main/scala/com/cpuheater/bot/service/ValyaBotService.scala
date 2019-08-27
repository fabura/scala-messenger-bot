package com.cpuheater.bot.service

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.cpuheater.bot.db.{BotDao, InMemoryDao}
import com.cpuheater.bot.db.DbModel.{Skill, StepId, SubSkill}
import com.cpuheater.bot.model
import com.cpuheater.bot.model.{FBMessage, FBMessageEventIn, FBMessageEventOut, FBPostback, FBRecipient}
import com.cpuheater.bot.util.HttpClient
import com.cpuheater.bot.json.BotJson._
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

    val function = Steps.flows.get(flowId)
      .flatMap(_.find(_ == stepId))
      .flatMap(stepsRunner.steps.get)

    if (function.isDefined) {
      logger.debug(s"Cannot find step for user: $senderId, $flowId, $stepId")
    } else {
      function.get.action.apply(me)
      val (newFlowId, newStepId) = Steps.nextStep(flowId, stepId)
      dao.getUser(senderId).map(_.copy(flowId = newFlowId, stepId = newStepId)).foreach(dao.saveUser)
    }
  }

  def handlePostBack(senderId: String, postback: FBPostback)(implicit ec: ExecutionContext, system: ActorSystem, materializer: ActorMaterializer): Unit = {
    postback.payload match {
      case Some("start") =>
        val response = FBMessageEventOut(recipient = FBRecipient(senderId),
          message = FBMessage(text = Some(s"Введите имя и фамилию"),
            metadata = Some("lol")))
        HttpClient.post(response)
      case Some(_) => logger.debug(s"Unknown postback: $postback")
      case None => logger.debug(s"Empty payload: $postback")
    }
  }

  def handleMessage(senderId: String, message: FBMessage)(implicit ec: ExecutionContext, system: ActorSystem, materializer: ActorMaterializer): Unit = {
    message.text match {
      case Some(text) =>
        val fbMessage = FBMessageEventOut(recipient = FBRecipient(senderId),
          message = FBMessage(text = Some(s"Scala messenger bot: $text"),
            metadata = Some("DEVELOPER_DEFINED_METADATA")))
        HttpClient.post(fbMessage).map(_ => ())
      case None => logger.info("No text in message")
    }
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
        message = FBMessage(text = Some(s"Введите имя и фамилию"),
          metadata = Some("lol")))
      HttpClient.post(response)
    }),
    Step(Steps.AddName, (me: FBMessageEventIn) => {
      val senderId = me.sender.id
      val updatedUser = getUser(senderId).flatMap(u => me.message.map(name => u.copy(name = name.text)))

      updatedUser.foreach(saveUser)
      if (updatedUser.isEmpty) logger.debug(s"User is empty! $senderId")
    }),
    Step(Steps.AskSkills, (me: FBMessageEventIn) => {
      HttpClient.post("Пожалуйста, введите навыки. (Валенька, эта стадия еще не готова окончательно. Пока можно просто строкой писать навыки")
    }),
    Step(Steps.AddSkills, (me: FBMessageEventIn) => {
      val senderId = me.sender.id
      val updatedUser = getUser(senderId).flatMap(u => me.message.flatMap(message => message.text.map(text => u.copy(skills = text.split("\\s+").map(Skill.apply).toSeq))))
      updatedUser.foreach(saveUser)
      if (updatedUser.isEmpty) logger.debug(s"User is empty! $senderId")
    }),
    Step(Steps.AskSubskills, (me: FBMessageEventIn) => {
      HttpClient.post("Пожалуйста, введите поднавыки. (Валенька, эта стадия еще не готова окончательно. Пока можно просто строкой писать навыки")
    }),
    Step(Steps.AddSubkills, (me: FBMessageEventIn) => {
      val senderId = me.sender.id
      val updatedUser = getUser(senderId).flatMap(u => me.message.flatMap(message => message.text.map(text => u.copy(subSkills = text.split("\\s+").map(SubSkill.apply).toSeq))))
      updatedUser.foreach(saveUser)
      if (updatedUser.isEmpty) logger.debug(s"User is empty! $senderId")
    }),
    Step(Steps.AskFrequency, (me: FBMessageEventIn) => {
      HttpClient.post("Пожалуйста, введите периодичность. (Валенька, эта стадия еще не готова окончательно. Пока можно написать \"2 недели\"")
    }),
    Step(Steps.AddFrequency, (me: FBMessageEventIn) => {
      val senderId = me.sender.id
      val updatedUser = getUser(senderId).flatMap(u => me.message.map(message => u.copy(frequency = message.text)))
      updatedUser.foreach(saveUser)
      if (updatedUser.isEmpty) logger.debug(s"User is empty! $senderId")
    }),
    Step(Steps.Finish, (me: FBMessageEventIn) => {
      HttpClient.post("Готово!")
    }),
    Step(Steps.Help, (me: FBMessageEventIn) => {
      HttpClient.post("Тут будет помощь!!")
    }),
  ).map(x => x.id -> x).toMap

}

object Steps {
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

  def nextStep(flowId: String, stepId: StepId): (String, StepId) = {
    val nextStep = flows.get(flowId).flatMap(_.dropWhile(_ != stepId).headOption)
    nextStep.map(flowId -> _).getOrElse("help" -> Help)
  }

  val flows = Map(
    "basic" -> (AskName :: AddName :: AskSkills :: AddSkills :: AskSubskills :: AddSubkills :: AskFrequency :: AddFrequency :: Finish :: Nil),
    "help" -> (Help :: Nil)
  )
}
