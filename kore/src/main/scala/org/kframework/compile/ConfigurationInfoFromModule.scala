package org.kframework.compile

import java.util

import org.kframework.POSet
import org.kframework.kore.ADT.{KList, KApply}

import scala.collection.JavaConverters._

import org.kframework.compile.ConfigurationInfo.Multiplicity
import org.kframework.definition.{Module, NonTerminal, Production}
import org.kframework.kore.{ADT, K, KLabel, Sort}
import org.kframework.TSort._

object ConfigurationInfoFromModule

class ConfigurationInfoFromModule(val m: Module) extends ConfigurationInfo {

  private val cellProductions: Map[Sort,Production] =
    m.productions.filter(_.att.contains("cell")).map(p => (p.sort, p)).toMap
  private val cellBagProductions: Map[Sort,Production] =
    m.productions.filter(_.att.contains("cellbag")).map(p => (p.sort, p)).toMap
  private val cellSorts: Set[Sort] = cellProductions.keySet
  private val cellBagSorts: Set[Sort] = cellBagProductions.keySet
  val cellLabels: Map[Sort, KLabel] = cellProductions.mapValues(_.klabel.get)
  private val cellInitializer: Map[Sort, K] =
    m.productions.filter(p => cellSorts(p.sort) && p.att.contains("initializer"))
      .map(p => (p.sort, KApply(p.klabel.get,KList(List.empty)))).toMap

  private val edges: Set[(Sort, Sort)] = cellProductions.toList.flatMap { case (s,p) =>
    p.items.flatMap{
      case NonTerminal(n) if cellSorts.contains(n) => List((s, n))
      case NonTerminal(n) if cellBagSorts.contains(n) => m.definedSorts.filter(m.subsorts.directlyGreaterThan(n, _)).map(subsort => (s, subsort))
      case _ => List()
    }}.toSet

  private val edgesPoset: POSet[Sort] = POSet(edges)

  private val topCells = cellSorts.filter (l => !edges.map(_._2).contains(l))

  if (topCells.size > 1)
    throw new AssertionError("Too many top cells:" + topCells)

  val topCell: Sort = topCells.head
  private val sortedSorts: Seq[Sort] = tsort(edges).toSeq
  val levels: Map[Sort, Int] = edges.toList.sortWith((l, r) => sortedSorts.indexOf(l._1) < sortedSorts.indexOf(r._1)).foldLeft(Map(topCell -> 0)) {
    case (m: Map[Sort, Int], (from: Sort, to: Sort)) =>
      m + (to -> (m(from) + 1))
  }

  private val mainCell = {
    val mainCells = cellProductions.filter(x => x._2.att.contains("maincell")).map(_._1)
    if (mainCells.size > 1)
      throw new AssertionError("Too many main cells:" + mainCells)
    if (mainCells.isEmpty)
      throw new AssertionError("No main cell found")
    mainCells.head
  }

  override def getLevel(k: Sort): Int = levels(k)
  override def isParentCell(k: Sort): Boolean = edges exists { case (c, _) => c == k }

  // todo: Cosmin: very, very approximate implementation -- will have to think about it
  override def getMultiplicity(k: Sort): Multiplicity =
    if (m.productionsFor(cellLabels(k)).exists(_.att.contains("assoc")))
      Multiplicity.STAR
    else
      Multiplicity.ONE

  override def getParent(k: Sort): Sort = edges collectFirst { case (p, `k`) => p } get
  override def isCell(k: Sort): Boolean = cellSorts.contains(k)
  override def isLeafCell(k: Sort): Boolean = !isParentCell(k)

  override def getChildren(k: Sort): util.List[Sort] = edges.toList.collect { case (`k`,p) => p }.asJava

  override def leafCellType(k: Sort): Sort = cellProductions(k).items.collectFirst{ case NonTerminal(n) => n} get

  override def getDefaultCell(k: Sort): K = cellInitializer(k)

  override def getCellLabel(k: Sort): KLabel = cellLabels(k)

  override def getRootCell: Sort = topCell
  override def getComputationCell: Sort = mainCell
}
