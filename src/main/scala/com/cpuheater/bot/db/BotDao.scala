package com.cpuheater.bot.db

import com.cpuheater.bot.db.DbModel.{StepId, User}

trait BotDao {

  def getStepId(userId: String): Option[StepId]

  def updateUserStepId(userId: String, step: StepId)

  def getUser(userId: String): Option[User]

  def saveUser(user: User): Unit
}