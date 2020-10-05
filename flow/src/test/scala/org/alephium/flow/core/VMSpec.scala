package org.alephium.flow.core

import org.alephium.flow.FlowFixture
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{ChainIndex, ContractOutputRef, TxOutputRef}
import org.alephium.protocol.vm.Val
import org.alephium.protocol.vm.lang.Compiler
import org.alephium.util.{AlephiumSpec, AVector, U64}

class VMSpec extends AlephiumSpec {
  it should "not start with private function" in new FlowFixture {
    val input =
      s"""
         |TxScript Foo {
         |  fn add() -> () {
         |    return
         |  }
         |}
         |""".stripMargin
    val script = Compiler.compileTxScript(input).toOption.get

    val chainIndex = ChainIndex.unsafe(0, 0)
    val block      = mine(blockFlow, chainIndex, txScriptOption = Some(script))
    assertThrows[RuntimeException](addAndCheck(blockFlow, block, 1))
  }

  trait CallFixture extends FlowFixture {
    def access: String

    lazy val input0 =
      s"""
         |TxContract Foo(mut x: U64) {
         |  $access fn add(a: U64) -> () {
         |    x = x + a
         |    if (a > 0) {
         |      add(a - 1)
         |    }
         |    return
         |  }
         |}
         |""".stripMargin
    lazy val script0      = Compiler.compileContract(input0).toOption.get
    lazy val initialState = AVector[Val](Val.U64(U64.Zero))

    lazy val chainIndex = ChainIndex.unsafe(0, 0)
    lazy val block0 =
      mine(blockFlow, chainIndex, outputScriptOption = Some(script0 -> initialState))
    lazy val contractOutputRef0 = TxOutputRef.unsafe(block0.transactions.head, 0)
    lazy val contractKey0       = contractOutputRef0.key

    lazy val input1 =
      s"""
         |TxContract Foo(mut x: U64) {
         |  pub fn add(a: U64) -> () {
         |    x = x + a
         |    if (a > 0) {
         |      add(a - 1)
         |    }
         |    return
         |  }
         |}
         |
         |TxScript Bar {
         |  pub fn call() -> () {
         |    let foo = Foo(@${contractKey0.toHexString})
         |    foo.add(4)
         |    return
         |  }
         |}
         |""".stripMargin
  }

  it should "not call external private function" in new CallFixture {
    val access: String = ""

    contractOutputRef0 is a[ContractOutputRef]
    addAndCheck(blockFlow, block0, 1)
    checkState(blockFlow, chainIndex, contractKey0, initialState)

    val script1 = Compiler.compileTxScript(input1, 1).toOption.get
    val block1  = mine(blockFlow, chainIndex, txScriptOption = Some(script1))
    assertThrows[RuntimeException](addAndCheck(blockFlow, block1, 2))
  }

  it should "handle contract states" in new CallFixture {
    val access: String = "pub"

    contractOutputRef0 is a[ContractOutputRef]
    addAndCheck(blockFlow, block0, 1)
    checkState(blockFlow, chainIndex, contractKey0, initialState)

    val script1   = Compiler.compileTxScript(input1, 1).toOption.get
    val newState1 = AVector[Val](Val.U64(U64.unsafe(10)))
    val block1    = mine(blockFlow, chainIndex, txScriptOption = Some(script1))
    addAndCheck(blockFlow, block1, 2)
    checkState(blockFlow, chainIndex, contractKey0, newState1)

    val newState2 = AVector[Val](Val.U64(U64.unsafe(20)))
    val block2    = mine(blockFlow, chainIndex, txScriptOption = Some(script1))
    addAndCheck(blockFlow, block2, 3)
    checkState(blockFlow, chainIndex, contractKey0, newState2)
  }

  it should "use latest worldstate when call external functions" in new FlowFixture {
    val chainIndex = ChainIndex.unsafe(0, 0)

    def createContract(input: String): Hash = {
      val script       = Compiler.compileContract(input).toOption.get
      val initialState = AVector[Val](Val.U64(U64.Zero))

      val block =
        mine(blockFlow, chainIndex, outputScriptOption = Some(script -> initialState))
      val contractOutputRef = TxOutputRef.unsafe(block.transactions.head, 0)
      val contractKey       = contractOutputRef.key

      contractOutputRef is a[ContractOutputRef]
      blockFlow.add(block).isRight is true
      checkState(blockFlow, chainIndex, contractKey, initialState)
      contractKey
    }

    val input0 =
      s"""
         |TxContract Foo(mut x: U64) {
         |  pub fn get() -> (U64) {
         |    return x
         |  }
         |
         |  pub fn foo(foo: ByteVec, bar: ByteVec) -> () {
         |    x = x + 10
         |    x = Bar(bar).bar(foo)
         |    return
         |  }
         |}
         |
         |TxContract Bar(mut x: U64) {
         |  pub fn bar(foo: ByteVec) -> (U64) {
         |    return Foo(foo).get() + 100
         |  }
         |}
         |""".stripMargin
    val contractKey0 = createContract(input0)

    val input1 =
      s"""
         |TxContract Bar(mut x: U64) {
         |  pub fn bar(foo: ByteVec) -> (U64) {
         |    return Foo(foo).get() + 100
         |  }
         |}
         |
         |TxContract Foo(mut x: U64) {
         |  pub fn get() -> (U64) {
         |    return x
         |  }
         |
         |  pub fn foo(foo: ByteVec, bar: ByteVec) -> () {
         |    x = x + 10
         |    x = Bar(bar).bar(foo)
         |    return
         |  }
         |}
         |
         |""".stripMargin
    val contractKey1 = createContract(input1)

    val main =
      s"""
         |TxScript Main {
         |  pub fn main() -> () {
         |    let foo = Foo(@${contractKey0.toHexString})
         |    foo.foo(@${contractKey0.toHexString}, @${contractKey1.toHexString})
         |  }
         |}
         |
         |TxContract Foo(mut x: U64) {
         |  pub fn get() -> (U64) {
         |    return x
         |  }
         |
         |  pub fn foo(foo: ByteVec, bar: ByteVec) -> () {
         |    x = x + 10
         |    x = Bar(bar).bar(foo)
         |    return
         |  }
         |}
         |""".stripMargin
    val script   = Compiler.compileTxScript(main).toOption.get
    val newState = AVector[Val](Val.U64(U64.unsafe(110)))
    val block    = mine(blockFlow, chainIndex, txScriptOption = Some(script))
    blockFlow.add(block).isRight is true
    checkState(blockFlow, chainIndex, contractKey0, newState)
  }
}