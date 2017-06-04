package hackling.internal

import java.io.File
import java.net.URI

import ammonite.ops._

private[hackling] object util {

  def uri(string: String) = new URI(string)

  private val unsafeChars: Set[Char] = " %$&+,:;=?@<>#".toSet
  // Scala version of http://stackoverflow.com/questions/4571346/how-to-encode-url-to-avoid-special-characters-in-java/4605848#4605848
  // '/' was removed from the unsafe character list
  // TODO this should be a libling ;)
  private def escape(input: String): String = {

    def toHex(ch: Int) =
      (if (ch < 10) '0' + ch else 'A' + ch - 10).toChar

    def isUnsafe(ch: Char) =
      ch > 128 || ch < 0 || unsafeChars(ch)

    input.flatMap {
      case ch if isUnsafe(ch) =>
        "%" + toHex(ch / 16) + toHex(ch % 16)
      case other =>
        other.toString
    }
  }

  // stolen from https://github.com/coursier/coursier/blob/master/cache/src/main/scala/coursier/Cache.scala
  def localFile(cache: File)(url: String): File = {
    val path =
      if (url.startsWith("file:///"))
        url.stripPrefix("file://")
      else if (url.startsWith("file:/"))
        url.stripPrefix("file:")
      else
      // FIXME Should we fully parse the URL here?
      // FIXME Should some safeguards be added against '..' components in paths?
        url.split(":", 2) match {
          case Array(protocol, remaining) =>
            val remaining0 =
              if (remaining.startsWith("///"))
                remaining.stripPrefix("///")
              else if (remaining.startsWith("/"))
                remaining.stripPrefix("/")
              else
                throw new Exception(s"URL $url doesn't contain an absolute path")

            val remaining1 =
              if (remaining0.endsWith("/"))
              // keeping directory content in .directory files
                remaining0 + ".directory"
              else
                remaining0

            new File(
              cache,
              escape(protocol + "/" + remaining1.dropWhile(_ == '/'))
            ).toString

          case _ =>
            throw new Exception(s"No protocol found in URL $url")
        }

    new File(path)
  }

  def normalizedUriString(uri: URI) = uri.normalize().toASCIIString
}