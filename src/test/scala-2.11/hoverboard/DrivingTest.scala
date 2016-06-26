package hoverboard

import hoverboard.term.Term
import org.scalacheck.Arbitrary
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Matchers, FlatSpec}

import scala.concurrent.{Await, Future, ExecutionContext}
import scala.concurrent.duration._

class DrivingTest extends FlatSpec with Matchers with PropertyChecks {

  import Util._
  import ExecutionContext.Implicits._

  implicit val termArb = Arbitrary(Arbitraries.term)
  implicit val program: Program = Program.prelude


  "driving" should "perform beta reduction" in {
    t"(fn x -> x) y".drive shouldEqual t"y"
    t"(fn x x -> f x) y z".drive shouldEqual t"f z"
    t"(fn x -> Add x z) (Add x y)".drive shouldEqual t"Add (Add x y) z".drive
  }

  it should "distribute case onto case" in {
    t"case (case x | 0 -> a | Suc x' -> b end) | 0 -> c end".drive shouldEqual
      t"case x | 0 -> (case a | 0 -> c end) | Suc x' -> (case b | 0 -> c end) end"
  }

  it should "distribute app onto case" in {
    t"(case x | 0 -> f end) y z".drive shouldEqual t"case x | 0 -> f y z end"
  }

  it should "reduce case of inj" in {
    t"case Suc x | 0 -> a | Suc b -> f b end".drive shouldEqual t"f x"
  }

  it should "remove constant fixed-point arguments" in {
    t"Add".drive shouldEqual t"fn x y -> (fix f x -> case x | 0 -> y | Suc x' -> Suc (f x') end) x"
    t"Append xs (Cons y Nil)".drive shouldEqual t"Snoc y xs".drive
  }

  it should "not introduce free variables" in {
    forAll { (t: Term) =>
      t.drive.freeVars.difference(t.freeVars) shouldBe empty
    }
  }

  it should "be idempotent" in {
    implicit val generatorDrivenConfig = PropertyCheckConfig(minSuccessful = 5)

    val historicalFails = Seq(
      t"Add 0 y",
      t"(fn x y -> case x | 0 -> Suc 0 | Suc x' -> Add y (Mul x' y) end) nat_1 (Suc (Suc (f nat_1)))")
    historicalFails
      .foreach { t => t.drive shouldEqual t.drive.drive }

    forAll { (t: Term) =>
      val driven = t.drive
      driven shouldEqual driven.drive
    }
  }

  it should "not simplify undriveable terms" in {
    t"Lt x y".drive shouldEqual t"Lt x y"
  }

  it should "unfold fixed points with constructor arguments safely" in {
    t"Add (Suc x) y".drive shouldEqual t"Suc (Add x y)".drive
    t"Reverse (Cons x xs)".drive shouldEqual t"Append (Reverse xs) (Cons x Nil)".drive
  }

  it should "not unfold fixed points with constructor arguments unsafely" in {
    t"Lt x (Suc x)".drive shouldEqual t"Lt x (Suc x)"
    t"LtEq (Suc x) x".drive shouldEqual t"LtEq (Suc x) x"
  }

  it should "not add fixed-point indices" in {
    forAll { (t: Term) => t.drive.indices.isSubsetOf(t.indices) shouldBe true }
  }
}
