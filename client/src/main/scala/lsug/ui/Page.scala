package lsug
package ui

sealed trait Page

object Page {
  case object Home extends Page
  case object Sponsors extends Page
  case class Event(meetupId: String, eventId: Int) extends Page
}
