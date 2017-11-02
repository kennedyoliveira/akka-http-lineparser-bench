package akka.http.scaladsl.unmarshalling.sse

import java.nio.file.{Files, Paths}
import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Keep, RunnableGraph, Sink, Source}
import akka.util.ByteString
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.options.OptionsBuilder

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@Fork(1)
class LineParserBenchmark {

  implicit val system: ActorSystem = ActorSystem("line-parser-benchmark")
  implicit val mat: ActorMaterializer = ActorMaterializer()

  @Param(Array(
    "1024",     // around 1   KB event
    "130945",   // around 128 KB event
    "523777",   // around 512 KB event
    "1129129",  // around 1   MB event
    "2095105",  // around 2   MB event
  ))
  var lineSize = 0

  // checking different chunk sizes because of chunk size direct impact on algorithm
  @Param(Array(
    "512",
    "1024",
    "2048",
    "4096",
    "8192"
  ))
  var chunkSize = 0

  lazy val line = ByteString("x" * lineSize + "\n")

  var defaultImplGraph: RunnableGraph[Future[Done]] = _
  var improvedImplGraph: RunnableGraph[Future[Done]] = _

  @Setup
  def setup(): Unit = {
    val tempFile = Files.createTempFile("akka-http-linear-bench", s"-$lineSize")
    val makeSampleFile = Source.single(line)
      .runWith(FileIO.toPath(tempFile))

    Await.result(makeSampleFile, 5 seconds)

    defaultImplGraph = FileIO
      .fromPath(tempFile, chunkSize)
      .via(new LineParser(lineSize + 1))
      .toMat(Sink.ignore)(Keep.right)

    improvedImplGraph = FileIO
      .fromPath(tempFile, chunkSize)
      .via(new ImprovedLineParser(lineSize + 1))
      .toMat(Sink.ignore)(Keep.right)
  }

  @TearDown
  def tearDown(): Unit = {
    Await.result(system.terminate(), 5.seconds)
  }

  @Benchmark
  def baseline(): Unit = {
    Await.result(defaultImplGraph.run(), Duration.Inf)
  }

  @Benchmark
  def improved(): Unit = {
    Await.result(improvedImplGraph.run(), Duration.Inf)
  }
}

object LineParserBenchmark {

  def main(args: Array[String]): Unit = {
    val jfr = s"""${System.getProperty("user.dir")}/target/jfr/${UUID.randomUUID()}"""
    Paths.get(jfr).toFile.mkdirs()

    val options = new OptionsBuilder()
      .include(classOf[LineParserBenchmark].getName)
      .addProfiler(classOf[jmh.extras.JFR], s"""--dir=$jfr""")
      .build()

    new Runner(options).run()
  }
}
