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

package org.alephium.wallet

import java.net.InetAddress

import io.vertx.core.Vertx
import io.vertx.ext.web._
import io.vertx.ext.web.handler.BodyHandler
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import sttp.model.StatusCode

import org.alephium.api.{ApiError, ApiModelCodec}
import org.alephium.api.UtilJson.avectorReadWriter
import org.alephium.api.model._
import org.alephium.crypto.wallet.Mnemonic
import org.alephium.http.HttpFixture._
import org.alephium.http.HttpRouteFixture
import org.alephium.json.Json._
import org.alephium.protocol.{Hash, SignatureSchema}
import org.alephium.protocol.config.GroupConfig
import org.alephium.protocol.model.{Address, CliqueId, NetworkType, TxGenerators}
import org.alephium.serde.serialize
import org.alephium.util.{discard, AlephiumFutureSpec, AVector, Duration, Hex, U256}
import org.alephium.wallet.api.model
import org.alephium.wallet.config.WalletConfigFixture
import org.alephium.wallet.json.ModelCodecs

class WalletAppSpec
    extends AlephiumFutureSpec
    with ModelCodecs
    with WalletConfigFixture
    with HttpRouteFixture
    with IntegrationPatience {

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val blockFlowMock =
    new WalletAppSpec.BlockFlowServerMock(host, blockFlowPort, networkType)

  val walletApp: WalletApp =
    new WalletApp(config)

  val port: Int = config.port.get

  walletApp.start().futureValue is ()

  val password                   = Hash.generate.toHexString
  var mnemonic: Mnemonic         = _
  var addresses: model.Addresses = _
  var address: Address           = _
  var wallet: String             = "wallet-name"
  val (_, transferPublicKey)     = SignatureSchema.generatePriPub()
  val transferAddress            = Address.p2pkh(networkType, transferPublicKey).toBase58
  val transferAmount             = 10
  val balanceAmount              = U256.unsafe(42)

  def creationJson(size: Int, maybeName: Option[String]) =
    s"""{"password":"$password","mnemonicSize":${size}${maybeName
      .map(name => s""","walletName":"$name"""")
      .getOrElse("")}}"""
  val unlockJson = s"""{"password":"$password"}"""
  val deleteJson = s"""{"password":"$password"}"""
  def transferJson(amount: Int) =
    s"""{"destinations":[{"address":"$transferAddress","amount":"$amount"}]}"""
  def changeActiveAddressJson(address: Address) = s"""{"address":"${address.toBase58}"}"""
  def restoreJson(mnemonic: Mnemonic) =
    s"""{"password":"$password","mnemonic":${writeJs(mnemonic)}}"""

  def create(size: Int, maybeName: Option[String] = None) =
    Post("/wallets", creationJson(size, maybeName))
  def restore(mnemonic: Mnemonic) = Put("/wallets", restoreJson(mnemonic))
  def unlock()                    = Post(s"/wallets/$wallet/unlock", unlockJson)
  def lock()                      = Post(s"/wallets/$wallet/lock")
  def delete()                    = Delete(s"/wallets/$wallet", deleteJson)
  def getBalance()                = Get(s"/wallets/$wallet/balances")
  def getAddresses()              = Get(s"/wallets/$wallet/addresses")
  def transfer(amount: Int)       = Post(s"/wallets/$wallet/transfer", transferJson(amount))
  def deriveNextAddress()         = Post(s"/wallets/$wallet/derive-next-address")
  def changeActiveAddress(address: Address) =
    Post(s"/wallets/$wallet/change-active-address", changeActiveAddressJson(address))
  def listWallets() = Get("/wallets")
  def getWallet()   = Get(s"/wallets/$wallet")

  it should "work" in {

    unlock() check { response =>
      response.code is StatusCode.NotFound
      response.body.leftValue is s"""{"resource":"$wallet","detail":"$wallet not found"}"""
    }

    create(2) check { response =>
      val error = response.as[ApiError.BadRequest]
      error.detail is s"""Invalid value for: body (Invalid mnemonic size: 2, expected: 12, 15, 18, 21, 24 at index 94: decoding failure)"""
      response.code is StatusCode.BadRequest
    }

    create(24) check { response =>
      val result = response.as[model.WalletCreation.Result]
      mnemonic = result.mnemonic
      wallet = result.walletName
      response.code is StatusCode.Ok
    }

    listWallets() check { response =>
      val walletStatus = response.as[AVector[model.WalletStatus]].head
      walletStatus.walletName is wallet
      walletStatus.locked is false
      response.code is StatusCode.Ok
    }

    getWallet() check { response =>
      val walletStatus = response.as[model.WalletStatus]
      walletStatus.walletName is wallet
      walletStatus.locked is false
      response.code is StatusCode.Ok
    }

    //Lock is idempotent
    (0 to 10).foreach { _ =>
      lock() check { response =>
        response.code is StatusCode.Ok
      }
    }

    getBalance() check { response =>
      response.code is StatusCode.Unauthorized
    }

    getAddresses() check { response =>
      response.code is StatusCode.Unauthorized
    }

    transfer(transferAmount) check { response =>
      response.code is StatusCode.Unauthorized
    }

    getWallet() check { response =>
      val walletStatus = response.as[model.WalletStatus]
      walletStatus.walletName is wallet
      walletStatus.locked is true
      response.code is StatusCode.Ok
    }

    unlock()

    getAddresses() check { response =>
      addresses = response.as[model.Addresses]
      address = addresses.activeAddress
      response.code is StatusCode.Ok
    }

    getBalance() check { response =>
      response.as[model.Balances] is model.Balances(
        balanceAmount,
        AVector(model.Balances.AddressBalance(address, balanceAmount))
      )
      response.code is StatusCode.Ok
    }

    transfer(transferAmount) check { response =>
      response.as[model.Transfer.Result]
      response.code is StatusCode.Ok
    }

    val negAmount = -10
    transfer(negAmount) check { response =>
      val error = response.as[ApiError.BadRequest]
      error.detail.contains(s"""Invalid value for: body (Invalid U256: $negAmount""") is true
      response.code is StatusCode.BadRequest
    }

    val tooMuchAmount = 10000
    transfer(tooMuchAmount) check { response =>
      val error = response.as[ApiError.BadRequest]
      error.detail.contains(s"""Not enough balance""") is true
      response.code is StatusCode.BadRequest
    }

    deriveNextAddress() check { response =>
      address = response.as[model.DeriveNextAddress.Result].address
      addresses = model.Addresses(address, addresses.addresses :+ address)
      response.code is StatusCode.Ok
    }

    getAddresses() check { response =>
      response.as[model.Addresses] is addresses
      response.code is StatusCode.Ok
    }

    address = addresses.addresses.head
    addresses = addresses.copy(activeAddress = address)

    changeActiveAddress(address) check { response =>
      response.code is StatusCode.Ok
    }

    getAddresses() check { response =>
      response.as[model.Addresses] is addresses
      response.code is StatusCode.Ok
    }

    val newMnemonic = Mnemonic.generate(24).get
    restore(newMnemonic) check { response =>
      wallet = response.as[model.WalletRestore.Result].walletName
      response.code is StatusCode.Ok
    }

    listWallets() check { response =>
      val walletStatuses = response.as[AVector[model.WalletStatus]]
      walletStatuses.length is 2
      walletStatuses.map(_.walletName).contains(wallet)
      response.code is StatusCode.Ok
    }

    Get("/docs") check { response =>
      response.code is StatusCode.Ok
    }

    Get("/docs/openapi.json") check { response =>
      response.code is StatusCode.Ok
    }

    create(24, Some("bad!name")) check { response =>
      response.code is StatusCode.BadRequest
    }

    create(24, Some("correct_wallet-name")) check { response =>
      response.code is StatusCode.Ok
    }

    delete() check { response =>
      response.code is StatusCode.Ok
    }

    delete() check { response =>
      response.code is StatusCode.NotFound
      write(
        response.as[ujson.Value]
      ) is s"""{"resource":"$wallet","detail":"$wallet not found"}"""
    }

    tempSecretDir.toFile.listFiles.foreach(_.deleteOnExit())
    walletApp.stop().futureValue is ()
  }
}

object WalletAppSpec extends {

  class BlockFlowServerMock(address: InetAddress, port: Int, val networkType: NetworkType)(implicit
      val groupConfig: GroupConfig
  ) extends TxGenerators
      with ApiModelCodec
      with ScalaFutures {

    private val cliqueId = CliqueId.generate
    private val peer     = PeerAddress(address, port, port, port)

    val blockflowFetchMaxAge = Duration.unsafe(1000)

    private val vertx  = Vertx.vertx()
    private val router = Router.router(vertx)

    def complete[A: Writer](ctx: RoutingContext, a: A, code: Int = 200): Unit = {
      discard(
        ctx.request
          .response()
          .setStatusCode(code)
          .putHeader("content-type", "application/json")
          .end(write(a))
      )
    }

    router.route().path("/transactions/build").handler(BodyHandler.create()).handler { ctx =>
      val buildTransaction = read[BuildTransaction](ctx.getBodyAsString())
      val amount = buildTransaction.destinations.fold(U256.Zero) { (acc, destination) =>
        acc.addUnsafe(destination.amount)
      }
      val unsignedTx = transactionGen().sample.get.unsigned

      if (amount > 100) {
        complete(
          ctx,
          ApiError.BadRequest("Not enough balance"),
          400
        )
      } else {
        complete(
          ctx,
          BuildTransactionResult(
            Hex.toHexString(serialize(unsignedTx)),
            unsignedTx.hash,
            unsignedTx.fromGroup.value,
            unsignedTx.toGroup.value
          )
        )
      }
    }

    router.route().path("/transactions/submit").handler(BodyHandler.create()).handler { ctx =>
      val _ = read[SubmitTransaction](ctx.getBodyAsString())
      complete(ctx, TxResult(Hash.generate, 0, 0))
    }

    router.route().path("/infos/self-clique").handler { ctx =>
      complete(ctx, SelfClique(cliqueId, NetworkType.Mainnet, 18, AVector(peer, peer), true, 1, 2))
    }

    router.route().path("/addresses/:address/balance").handler { ctx =>
      complete(ctx, Balance(42, 21, 1))
    }

    private val server = vertx.createHttpServer().requestHandler(router)
    server.listen(port, address.getHostAddress)
  }
}
