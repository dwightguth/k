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

trait SymbolReference extends Term {
  val symbol: Alphabet
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
case class Terminal(regex: String, isLayout: Boolean) extends Term with SymbolReference with Alphabet {
  val symbol = this
  def shallowCopy(location: Location, source: Source) = Terminal(regex, isLayout, location, source)
}

case class Constant private(value: String, production: Production, symbol: Alphabet) extends ProductionReference with SymbolReference {
  def shallowCopy(location: Location, source: Source) = Constant(value, production, symbol, location, source)
  override def toString = "#token(" + production.sort + ",\"" + StringEscapeUtils.escapeJava(value) + "\")"
}

case class TermCons private(items: List[Term], production: Production, symbol: Alphabet)
  extends ProductionReference with HasChildren with SymbolReference {
  def shallowCopy(location: Location, source: Source) = TermCons(items, production, symbol, location, source)

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

case class Ambiguity(items: Set[Term], symbol: Alphabet)
  extends Term with HasChildren with SymbolReference {
  def shallowCopy(location: Location, source: Source) = Ambiguity(items, symbol, location, source)
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
  def apply(value: String, production: Production, symbol: Alphabet, location: Location, source: Source):Constant = {
    val res = Constant(value, production, symbol)
    res.location = Optional.of(location)
    res.source = Optional.of(source)
    res
  }
}

object TermCons {
  def apply(items: List[Term], production: Production, symbol: Alphabet, location: Location, source: Source):TermCons = {
    val res = TermCons(items, production, symbol)
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
  @annotation.varargs def apply(symbol: Alphabet, items: Term*): Ambiguity = Ambiguity(items.toSet.asJava, symbol)
  def apply(items: Set[Term], symbol: Alphabet, location: Location, source: Source):Ambiguity = {
    val res = Ambiguity(items, symbol)
    res.location = Optional.of(location)
    res.source = Optional.of(source)
    res
  }
}

object Terminal {
  def apply(regex: String, isLayout: Boolean, location: Location, source: Source):Terminal = {
    val res = Terminal(regex, isLayout)
    res.location = Optional.of(location)
    res.source = Optional.of(source)
    res
  }
}