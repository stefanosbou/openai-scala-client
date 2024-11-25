import Dependencies.Versions._

name := "openai-scala-client-stream"

description := "Stream support for the OpenAI Scala client."

libraryDependencies ++= Seq(
  "org.apache.pekko" %% "pekko-http" % "1.0.1", // JSON WS Streaming
  "io.cequence" %% "ws-client-core" % wsClient,
  "io.cequence" %% "ws-client-play" % wsClient,
  "io.cequence" %% "ws-client-play-stream" % wsClient
)
