/**
 * NOTE: Code snippets from this test are included in the Actor Migration Guide. In case you change
 * code in these tests prior to the 2.10.0 release please send the notification to @vjovanov.
 */
import scala.actors._
import scala.actors.migration._
import scala.concurrent.duration._
import scala.concurrent.{ Promise, Await }

class PinS2 extends PartestSuite {
  val checkFile = "actormig-PinS_2"
  import org.junit._
  object SillyActor {
    val startPromise = Promise[Boolean]()
    val ref = ActorDSL.actor(new SillyActor)
  }

  /* PinS, Listing 32.1: A simple actor
 */
  class SillyActor extends ActWithStash {

    def receive = { case _ => println("Nop") }

    override def act() {
      Await.ready(SillyActor.startPromise.future, 5 seconds)
      for (i <- 1 to 5)
        println("I'm acting!")

      println("Post stop")
    }
  }

  object SeriousActor {
    val startPromise = Promise[Boolean]()
    val ref = ActorDSL.actor(new SeriousActor)
  }

  class SeriousActor extends ActWithStash {
    def receive = { case _ => println("Nop") }
    override def act() {
      Await.ready(SeriousActor.startPromise.future, 5 seconds)
      for (i <- 1 to 5)
        println("To be or not to be.")
    }
  }

  /* PinS, Listing 32.3: An actor that calls react
 */
  object NameResolver {
    val ref = ActorDSL.actor(new NameResolver)
  }

  class NameResolver extends ActWithStash {
    import java.net.{ InetAddress, UnknownHostException }

    def receive = { case _ => println("Nop") }

    override def act() {
      react {
        case (name: String, actor: ActorRef) =>
          actor ! getIp(name)
          act()
        case "EXIT" =>
          println("Name resolver exiting.")
        // quit
        case msg =>
          println("Unhandled message: " + msg)
          act()
      }
    }

    def getIp(name: String): Option[InetAddress] = {
      try {
        Some(InetAddress.getByName(name))
      } catch {
        case _: UnknownHostException => None
      }
    }

  }

  @Test
  def test(): Unit = {

    /* PinS, Listing 32.2: An actor that calls receive
   */
    def makeEchoActor(): ActorRef = ActorDSL.actor(
      new ActWithStash {
        def receive = { case _ => println("Nop") }

        override def act() {
          loop {
            react {
              case 'stop =>
                exit()
              case msg =>
                println("received message: " + msg)
            }
          }
        }
      })

    /* PinS, page 696
   */
    def makeIntActor(): ActorRef = ActorDSL.actor(new ActWithStash {

      def receive = { case _ => println("Nop") }

      override def act() {
        react {
          case x: Int => // I only want Ints
            println("Got an Int: " + x)
        }
      }
    })

    ActorDSL.actor(new ActWithStash {

      def receive = { case _ => println("Nop") }

      override def act() {
        trapExit = true
        link(SillyActor.ref)
        SillyActor.startPromise.success(true)
        react {
          case Exit(_: SillyActor, _) =>
            link(SeriousActor.ref)
            SeriousActor.startPromise.success(true)
            react {
              case Exit(_: SeriousActor, _) =>
                val seriousPromise2 = Promise[Boolean]()
                // PinS, page 694
                val seriousActor2 = ActorDSL.actor(
                  new ActWithStash {

                    def receive = { case _ => println("Nop") }

                    override def act() {
                      for (i <- 1 to 5)
                        println("That is the question.")
                      seriousPromise2.success(true)
                    }
                  })

                Await.ready(seriousPromise2.future, 5 seconds)
                val echoActor = makeEchoActor()
                link(echoActor)
                echoActor ! "hi there"
                echoActor ! 15
                echoActor ! 'stop
                react {
                  case Exit(_, _) =>
                    val intActor = makeIntActor()
                    intActor ! "hello"
                    intActor ! math.Pi
                    // only the following send leads to output
                    intActor ! 12
                }
            }
        }
      }
    })
  }
}