package it.gov.daf.sso

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets

import it.gov.daf.securitymanager.utilities.ConfigReader
import play.api.Logger

import scala.concurrent.Future
import scala.sys.process.{Process, ProcessLogger}
import play.api.libs.concurrent.Execution.Implicits._

object BasicAuthToSPNEGO {

  def loginF(usrName:String, pwd:String, service:KerberosService):Future[Either[String,String]] = {
    Future{ login(usrName,pwd,service) }
  }

  def login(usrName:String, pwd:String, service:KerberosService):Either[String,String] = {

    val serviceUrl = service match{
      case HdfsService => ConfigReader.hadoopUrl+HdfsService.loginUrl
      case LivyService => ConfigReader.livyUrl+LivyService.loginUrl
    }

    val out = new StringBuilder("OUT:")
    val err = new StringBuilder("ERR:")
    val result = new StringBuilder

    try {

      val logger = ProcessLogger(
        (o: String) => {out.append(s"$o\n");()},
        (e: String) => {err.append(s"$e\n");()} )

      val scriptName =  if(System.getProperty("STAGING") != null) "./script/kb_init_local_test.sh"
      else "./script/kb_init.sh"


      val commandStr = s"timeout 10 $scriptName $usrName $serviceUrl"  // Process should hang: command timeout needed
      Logger.logger.debug(s"Launching $commandStr")
      val pb = Process(commandStr)

      val bos = new ByteArrayOutputStream()
      val exitCode = pb #< new ByteArrayInputStream(s"$pwd\n".toCharArray.map(_.toByte)) #> bos ! logger

      result.append(new String(bos.toByteArray, StandardCharsets.UTF_8))


      if (exitCode == 0) {
        Right(result.toString().split("\r?\n").filter(_.startsWith("Set-Cookie")).head.replaceFirst("Set-Cookie:", "").trim)
      } else if (exitCode == 1) {
        val outMsg = s"Error in kinit script  \n$result\n$out\n$err"
        Logger.logger.error(outMsg)
        Left(outMsg)
      }else {
        val outMsg = s"Error in kinit script: timeout  \n$result\n$out\n$err"
        Logger.logger.error(outMsg)
        Left(outMsg)
      }
    } catch {
      case t: Throwable => Left(s"Error in during webHDFS init \n$result\n$out\n$err")
    }

  }


}
