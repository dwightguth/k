package org.kframework

import org.kframework.parser.concrete2kore.ParseCache

import scala.pickling.Defaults._
import scala.pickling.binary._
import scala.pickling.shareNothing._

/**
 * Created by dwightguth on 4/22/15.
 */
object Pickling {

  def pickleParser(k: java.util.Map[String, ParseCache]) = k.pickle.value
  def unpickleParser(k: Array[Byte]) = BinaryPickle(k).unpickle[java.util.Map[String, ParseCache]]
}
