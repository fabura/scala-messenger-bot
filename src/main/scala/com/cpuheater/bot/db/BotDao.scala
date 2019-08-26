package com.cpuheater.bot.db

import com.cpuheater.bot.db.DbModel.{StepId, User}

trait BotDao {

  def getStepId(userId: String): Option[(String, StepId)]

  def updateUserStepId(userId: String, flowId: String, step: StepId)

  def getUser(userId: String): Option[User]

  def saveUser(user: User): Unit
}