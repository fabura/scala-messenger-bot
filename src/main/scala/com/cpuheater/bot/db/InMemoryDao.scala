package com.cpuheater.bot.db

import com.cpuheater.bot.db.DbModel._

class InMemoryDao extends BotDao {

  private val usersMap: scala.collection.mutable.Map[String, User] = scala.collection.mutable.Map()

  override def getStepId(userId: String): Option[StepId] = usersMap.get(userId).map(_.stepId)

  override def updateUserStepId(userId: String, step: DbModel.StepId): Unit = {
    val user = usersMap.getOrElseUpdate(userId, User(userId, step))
    val newUser = user.copy(stepId = step)
    usersMap.update(userId, newUser)
  }

  override def getUser(userId: String): Option[DbModel.User] = usersMap.get(userId)

  override def saveUser(user: DbModel.User): Unit = usersMap.update(user.id, user)
}
