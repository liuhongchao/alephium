// Copyright 2018 The Alephium Authors
// This file is part of the alephium project.
//
// The library is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// The library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the library. If not, see <http://www.gnu.org/licenses/>.

package org.alephium.util

import scala.concurrent.Await
import scala.language.implicitConversions

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestKitBase}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.concurrent.ScalaFutures

class AlephiumActorSpec(val name: String) extends AlephiumActorSpecLike

trait AlephiumActorSpecLike
    extends TestKitBase
    with ImplicitSender
    with AlephiumSpec
    with BeforeAndAfterAll {
  implicit def safeActor[T](ref: ActorRef): ActorRefT[T] = ActorRefT(ref)

  def name: String

  implicit lazy val system: ActorSystem =
    ActorSystem(name, ConfigFactory.parseString(AlephiumActorSpec.warningConfig))

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }
}

trait RefinedAlephiumActorSpec extends AlephiumSpec with BeforeAndAfterEach with ScalaFutures {
  var _system: ActorSystem = _

  override def afterEach(): Unit = {
    super.afterEach()
    if (_system != null) {
      Await.result(_system.terminate(), Duration.ofSecondsUnsafe(10).asScala)
      ()
    }
  }

  trait ActorCreation {
    implicit val system: ActorSystem =
      ActorSystem("test", ConfigFactory.parseString(AlephiumActorSpec.warningConfig))
    _system = system
  }

  trait ActorFixture extends ActorCreation with TestKitBase with ImplicitSender
}

object AlephiumActorSpec {
  lazy val warningConfig = config("WARNING")
  lazy val infoConfig    = config("INFO")
  lazy val debugConfig   = config("DEBUG")

  def config(logLevel: String): String =
    s"""
      |akka {
      |  loglevel = "$logLevel"
      |  loggers = ["akka.testkit.TestEventListener"]
      |  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
      |
      |  io.tcp.trace-logging = off
      |
      |  actor {
      |    debug {
      |      unhandled = on
      |    }
      |
      |    guardian-supervisor-strategy = "org.alephium.util.DefaultStrategy"
      |
      |    default-dispatcher {
      |      executor = "fork-join-executor"
      |      throughput = 1
      |    }
      |  }
      |}
    """.stripMargin
}
