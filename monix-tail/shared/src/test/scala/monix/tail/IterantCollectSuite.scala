/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.tail

import monix.eval.{Coeval, Task}
import monix.execution.exceptions.DummyException
import monix.tail.Iterant.Suspend
import org.scalacheck.Test.Parameters

import scala.util.Failure

object IterantCollectSuite extends BaseTestSuite {
  override lazy val checkConfig: Parameters =
    super.checkConfig.withMaxSize(64)

  test("Iterant.collect <=> List.collect") { implicit s =>
    check3 { (stream: Iterant[Task, Int], p: Int => Boolean, f: Int => Int) =>
      val pf: PartialFunction[Int,Int] = { case x if p(x) => f(x) }
      val received = stream.collect(pf).toListL
      val expected = stream.toListL.map(_.collect(pf))
      received === expected
    }
  }

  test("Iterant.collect protects against user error") { implicit s =>
    check1 { (stream: Iterant[Task, Int]) =>
      val dummy = DummyException("dummy")
      val received = (stream ++ Iterant[Task].now(1)).collect[Int] { case _ => throw dummy }
      received === Iterant[Task].raiseError(dummy)
    }
  }

  test("Iterant.collect flatMap equivalence") { implicit s =>
    check3 { (stream: Iterant[Task, Int], p: Int => Boolean, f: Int => Int) =>
      val pf: PartialFunction[Int,Int] = { case x if p(x) => f(x) }
      val received = stream.collect(pf)
      val expected = stream.flatMap(x => if (pf.isDefinedAt(x)) Iterant[Task].now(pf(x)) else Iterant[Task].empty[Int])
      received === expected
    }
  }

  test("Iterant.collect suspends the evaluation for NextGen") { _ =>
    val dummy = DummyException("dummy")
    val items = new ThrowExceptionIterable(dummy)
    val iter = Iterant[Coeval].nextGenS(items, Coeval.now(Iterant.empty[Coeval, Int]), Coeval.unit)
    val state = iter.collect { case x => (throw dummy): Int }

    assert(state.isInstanceOf[Suspend[Coeval,Int]], "state.isInstanceOf[Suspend[Coeval,Int]]")
    assert(!items.isTriggered, "!items.isTriggered")
    assertEquals(state.toListL.runTry, Failure(dummy))
  }

  test("Iterant.collect suspends the evaluation for NextSeq") { _ =>
    val dummy = DummyException("dummy")
    val items = new ThrowExceptionIterator(dummy)
    val iter = Iterant[Coeval].nextSeqS(items, Coeval.now(Iterant.empty[Coeval, Int]), Coeval.unit)
    val state = iter.collect { case x => (throw dummy): Int }

    assert(state.isInstanceOf[Suspend[Coeval,Int]], "state.isInstanceOf[Suspend[Coeval,Int]]")
    assert(!items.isTriggered, "!items.isTriggered")
    assertEquals(state.toListL.runTry, Failure(dummy))
  }

  test("Iterant.collect suspends the evaluation for Next") { _ =>
    val dummy = DummyException("dummy")
    val iter = Iterant[Coeval].nextS(1, Coeval.now(Iterant.empty[Coeval, Int]), Coeval.unit)
    val state = iter.collect { case x => (throw dummy): Int }

    assert(state.isInstanceOf[Suspend[Coeval,Int]], "state.isInstanceOf[Suspend[Coeval,Int]]")
    assertEquals(state.toListL.runTry, Failure(dummy))
  }

  test("Iterant.collect suspends the evaluation for Last") { _ =>
    val dummy = DummyException("dummy")
    val iter = Iterant[Coeval].lastS(1)
    val state = iter.collect { case x => (throw dummy): Int }

    assert(state.isInstanceOf[Suspend[Coeval,Int]])
    assertEquals(state.toListL.runTry, Failure(dummy))
  }

  test("Iterant.collect doesn't touch Halt") { _ =>
    val dummy = DummyException("dummy")
    val iter1: Iterant[Coeval, Int] = Iterant[Coeval].haltS(Some(dummy))
    val state1 = iter1.collect { case x => x }
    assertEquals(state1, iter1)

    val iter2: Iterant[Coeval, Int] = Iterant[Coeval].haltS(None)
    val state2 = iter2.collect { case x => (throw dummy) : Int }
    assertEquals(state2, iter2)
  }
}
