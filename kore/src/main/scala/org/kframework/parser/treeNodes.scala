// Copyright (c) 2014 K Team. All Rights Reserved.

package org.kframework.parser

import org.kframework.attributes.{Source, Location}
import org.kframework.definition.Production
import java.util._
import java.lang.Iterable
import collection.JavaConverters._
import org.apache.commons.lang3.StringEscapeUtils

trait Term {
  var location: Optional[Location] = Optional.empty()
  var source: Optional[Source] = Optional.empty()
  def shallowCopy(l: Location, s: Source): Term
}

trait ProductionReference extends Term {
  val production: Production
}

trait HasChildren {
  def items: Iterable[Term]
  def replaceChildren(newChildren: Collection[Term]): Term
}

trait Alphabet {
  val isLayout: Boolean
}
case class NonTerminal(sort: String) extends Alphabet {
  val isLayout = false
}
case class Terminal(regex: String, isLayout: Boolean) extends Alphabet {
}

case class Constant private(value: String, production: Production) extends ProductionReference {
  def shallowCopy(location: Location, source: Source) = Constant(value, production, location, source)
  override def toString = "#token(" + production.sort + ",\"" + StringEscapeUtils.escapeJava(value) + "\")"
}

case class TermCons private(items: List[Term], production: Production)
  extends ProductionReference with HasChildren {
  def shallowCopy(location: Location, source: Source) = TermCons(items, production, location, source)

  def replaceChildren(newChildren: Collection[Term]) = {
    items.clear(); items.addAll(newChildren);
    this
  }
  override def toString() = production.klabel.getOrElse("NOKLABEL") + "(" + (items.asScala mkString ",") + ")"

  var cachedHashCode: Option[Int] = None

  def invalidateHashCode() {
    cachedHashCode = None
  }

  override def hashCode = cachedHashCode match {
    case None =>
      cachedHashCode  = Some(items.asScala.map(_.hashCode).fold(production.hashCode * 37)( 31 * _ + _))
      cachedHashCode.get
    case Some(hc) => hc
  }
}

case class Ambiguity(items: Set[Term])
  extends Term with HasChildren {
  def shallowCopy(location: Location, source: Source) = Ambiguity(items, location, source)
  def replaceChildren(newChildren: Collection[Term]) = {
    items.clear(); items.addAll(newChildren);
    this
  }
  override def toString() = "amb(" + (items.asScala mkString ",") + ")"
}

case class KList(items: List[Term])
  extends Term with HasChildren {
  def add(t: Term) { items.add(t) }
  def shallowCopy(l: Location, source: Source) = KList(items, l, source)
  def replaceChildren(newChildren: Collection[Term]) = {
    items.clear(); items.addAll(newChildren);
    this
  }
  override def toString() = "[" + (items.asScala mkString ",") + "]"
}

object Constant {
  def apply(value: String, production: Production, location: Location, source: Source):Constant = {
    val res = Constant(value, production)
    res.location = Optional.of(location)
    res.source = Optional.of(source)
    res
  }
}

object TermCons {
  def apply(items: List[Term], production: Production, location: Location, source: Source):TermCons = {
    val res = TermCons(items, production)
    res.location = Optional.of(location)
    res.source = Optional.of(source)
    res
  }
}

object KList {
  @annotation.varargs def apply(ts: Term*): KList = KList(new ArrayList(ts.asJava))
  def apply(toCopy: KList): KList = KList(new ArrayList(toCopy.items)) // change when making the classes mutable
  def apply(items: List[Term], location: Location, source: Source):KList = {
    val res = KList(items)
    res.location = Optional.of(location)
    res.source = Optional.of(source)
    res
  }
}

object Ambiguity {
  @annotation.varargs def apply(items: Term*): Ambiguity = Ambiguity(items.toSet.asJava)
  def apply(items: Set[Term], location: Location, source: Source):Ambiguity = {
    val res = Ambiguity(items)
    res.location = Optional.of(location)
    res.source = Optional.of(source)
    res
  }
}