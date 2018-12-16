package org.alephium.util

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll

class AlephiumActorSpec(name: String)
    extends TestKit(ActorSystem(name, ConfigFactory.parseString(AlephiumActorSpec.config)))
    with ImplicitSender
    with AlephiumSpec
    with BeforeAndAfterAll {
  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }
}

object AlephiumActorSpec {
  val config: String =
    """
      |akka {
      |  loggers = ["akka.testkit.TestEventListener"]
      |  stdout-loglevel = "OFF"
      |  loglevel = "OFF"
      |
      |  actor {
      |    debug {
      |      unhandled = on
      |    }
      |  }
      |}
    """.stripMargin
}