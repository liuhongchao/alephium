package org.alephium.flow

import org.alephium.flow.io.IOResult
import org.alephium.protocol.model.FlowData
import org.alephium.serde.RandomBytes
import org.alephium.util.{AVector, Duration}

object Utils {
  val nodeMonitorPath: String   = "/user/NodeMonitor"
  val shutdownTimeout: Duration = Duration.ofSecondsUnsafe(10)

  def show[T <: RandomBytes](elems: AVector[T]): String = {
    elems.map(_.shortHex).mkString("-")
  }

  def showHash[T <: FlowData](elems: AVector[T]): String = {
    showHashIter(elems.toIterable)
  }

  def showHashIter[T <: FlowData](elems: Iterable[T]): String = {
    elems.view.map(_.shortHex).mkString("-")
  }

  def unsafe[T](e: IOResult[T]): T = e match {
    case Right(t) => t
    case Left(e)  => throw e
  }
}
