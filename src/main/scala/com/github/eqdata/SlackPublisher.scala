package com.github.eqdata

import akka.actor.{Actor, ActorSystem}
import com.github.eqdata.AuctionAgent.{Item, User}
import com.github.eqdata.Slack.Bot
import com.github.eqdata.cmd.PostAuction
import com.typesafe.config.ConfigException.BadValue
import com.typesafe.scalalogging.LazyLogging
import slack.api.SlackApiClient
import slack.models.Channel

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.{Failure, Success}

class SlackPublisher(token: String, channelName: String, bot: Bot) extends Actor with LazyLogging {

  implicit val system: ActorSystem = context.system

  val client = SlackApiClient(token)

  override def receive: Receive = {
    case Start =>
      logger.trace(s"starting $self")
      val sentFrom = sender
      val futureChannel: Future[Channel] =
        client
          .listChannels()
          .map { channels =>
            logger.trace(s"Found ${channels.size} channels: ${channels.map(_.name).sorted.mkString(", ")}")
            channels
              .find(_.name == channelName)
              .getOrElse(throw new BadValue("slack.channel", "no channel with this name"))
          }
      futureChannel.onComplete {
        case Success(channel) =>
          sentFrom ! Started
          context.become(ready(channel))
        case Failure(t) =>
          t.printStackTrace()
          sentFrom ! Stop
      }
  }

  private def ready(channel: Channel): Receive = {
    case PostAuction(User(name), items) =>
      val msg = items
        .map { i =>
          s"<https://wiki.project1999.com/${i.uri}|${i.name}>"
        }
        .toList
        .sorted
        .mkString(s"$name is selling ", ", ", ".")
      client.postChatMessage(channel.id, msg, username = Some(bot.name), iconUrl = Some(bot.iconUrl))

  }
}

object Slack {

  case class Bot(name: String, iconUrl: String)

}
