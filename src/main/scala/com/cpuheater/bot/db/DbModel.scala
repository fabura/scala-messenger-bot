package com.cpuheater.bot.db

object DbModel {

  case class StepId(id: String)

  case class Skill(/*id:String,*/ name: String)

  case class SubSkill(/*id: String, parentSkill: Skill, */ name: String)

  case class User(id: String, flowId: String, stepId: StepId, name: Option[String] = None, bio: Option[String] = None,
                  skills: Seq[Skill] = Nil, subSkills: Seq[SubSkill] = Nil, frequency: Option[String] = None)

}
