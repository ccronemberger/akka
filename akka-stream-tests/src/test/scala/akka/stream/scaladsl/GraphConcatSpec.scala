/**
 * Copyright (C) 2009-2014 Typesafe Inc. <http://www.typesafe.com>
 */
package akka.stream.scaladsl

import scala.concurrent.Promise

import akka.stream.scaladsl._
import akka.stream.scaladsl.FlowGraphImplicits._
import akka.stream.testkit.StreamTestKit
import akka.stream.testkit.TwoStreamsSetup

class GraphConcatSpec extends TwoStreamsSetup {

  override type Outputs = Int
  val op = Concat[Int]
  override def operationUnderTestLeft() = op.first
  override def operationUnderTestRight() = op.second

  "Concat" must {

    "work in the happy case" in {
      val probe = StreamTestKit.SubscriberProbe[Int]()

      FlowGraph { implicit b ⇒

        val concat1 = Concat[Int]
        val concat2 = Concat[Int]

        Source(List.empty[Int]) ~> concat1.first
        Source(1 to 4) ~> concat1.second

        concat1.out ~> concat2.first
        Source(5 to 10) ~> concat2.second

        concat2.out ~> Sink(probe)
      }.run()

      val subscription = probe.expectSubscription()

      for (i ← 1 to 10) {
        subscription.request(1)
        probe.expectNext(i)
      }

      probe.expectComplete()
    }

    commonTests()

    "work with one immediately completed and one nonempty publisher" in {
      val subscriber1 = setup(completedPublisher, nonemptyPublisher(1 to 4))
      val subscription1 = subscriber1.expectSubscription()
      subscription1.request(5)
      subscriber1.expectNext(1)
      subscriber1.expectNext(2)
      subscriber1.expectNext(3)
      subscriber1.expectNext(4)
      subscriber1.expectComplete()

      val subscriber2 = setup(nonemptyPublisher(1 to 4), completedPublisher)
      val subscription2 = subscriber2.expectSubscription()
      subscription2.request(5)
      subscriber2.expectNext(1)
      subscriber2.expectNext(2)
      subscriber2.expectNext(3)
      subscriber2.expectNext(4)
      subscriber2.expectComplete()
    }

    "work with one delayed completed and one nonempty publisher" in {
      val subscriber1 = setup(soonToCompletePublisher, nonemptyPublisher(1 to 4))
      val subscription1 = subscriber1.expectSubscription()
      subscription1.request(5)
      subscriber1.expectNext(1)
      subscriber1.expectNext(2)
      subscriber1.expectNext(3)
      subscriber1.expectNext(4)
      subscriber1.expectComplete()

      val subscriber2 = setup(nonemptyPublisher(1 to 4), soonToCompletePublisher)
      val subscription2 = subscriber2.expectSubscription()
      subscription2.request(5)
      subscriber2.expectNext(1)
      subscriber2.expectNext(2)
      subscriber2.expectNext(3)
      subscriber2.expectNext(4)
      subscriber2.expectComplete()
    }

    "work with one immediately failed and one nonempty publisher" in {
      val subscriber1 = setup(failedPublisher, nonemptyPublisher(1 to 4))
      subscriber1.expectErrorOrSubscriptionFollowedByError(TestException)

      val subscriber2 = setup(nonemptyPublisher(1 to 4), failedPublisher)
      subscriber2.expectErrorOrSubscriptionFollowedByError(TestException)
    }

    "work with one delayed failed and first nonempty publisher" in {
      val subscriber = setup(nonemptyPublisher(1 to 4), soonToFailPublisher)
      subscriber.expectSubscription().request(5)

      var errorSignalled = false
      if (!errorSignalled) errorSignalled ||= subscriber.expectNextOrError(1, TestException).isLeft
      if (!errorSignalled) errorSignalled ||= subscriber.expectNextOrError(2, TestException).isLeft
      if (!errorSignalled) errorSignalled ||= subscriber.expectNextOrError(3, TestException).isLeft
      if (!errorSignalled) errorSignalled ||= subscriber.expectNextOrError(4, TestException).isLeft
      if (!errorSignalled) subscriber.expectErrorOrSubscriptionFollowedByError(TestException)
    }

    "work with one delayed failed and second nonempty publisher" in {
      val subscriber = setup(soonToFailPublisher, nonemptyPublisher(1 to 4))
      subscriber.expectSubscription().request(5)

      var errorSignalled = false
      if (!errorSignalled) errorSignalled ||= subscriber.expectNextOrError(1, TestException).isLeft
      if (!errorSignalled) errorSignalled ||= subscriber.expectNextOrError(2, TestException).isLeft
      if (!errorSignalled) errorSignalled ||= subscriber.expectNextOrError(3, TestException).isLeft
      if (!errorSignalled) errorSignalled ||= subscriber.expectNextOrError(4, TestException).isLeft
      if (!errorSignalled) subscriber.expectErrorOrSubscriptionFollowedByError(TestException)
    }

    "correctly handle async errors in secondary upstream" in {
      val promise = Promise[Int]()
      val subscriber = StreamTestKit.SubscriberProbe[Int]()

      FlowGraph { implicit b ⇒
        val concat = Concat[Int]
        Source(List(1, 2, 3)) ~> concat.first
        Source(promise.future) ~> concat.second
        concat.out ~> Sink(subscriber)
      }.run()

      val subscription = subscriber.expectSubscription()
      subscription.request(4)
      subscriber.expectNext(1)
      subscriber.expectNext(2)
      subscriber.expectNext(3)
      promise.failure(TestException)
      subscriber.expectError(TestException)
    }
  }
}
