// Copyright (c) 2014 K Team. All Rights Reserved.

package org.kframework.definition

import org.kframework.definition
import org.kframework.utils.errorsystem.KEMException

object ModuleTransformer {
  def from(f: java.util.function.UnaryOperator[Module]): ModuleTransformer = ModuleTransformer(f(_))
  def fromSentenceTransformer(f: java.util.function.UnaryOperator[Sentence]): ModuleTransformer =
    ModuleTransformer(m => Module(m.name, m.imports, m.localSentences map { s =>
      try {
        f(s)
      } catch {
        case e: KEMException =>
          e.exception.addTraceFrame("while compiling sentence at " + s.att.get("org.kframework.attributes.Source").get
            + ":" + s.att.get("org.kframework.attributes.Location").get)
          throw e
      }
    }))

  def apply(f: Module => Module): ModuleTransformer = new ModuleTransformer(f)
}

/**
 * Transform all modules, transforming each module after its imports.
 */
class ModuleTransformer(f: Module => Module) extends (Module => Module) {
  val memoization = collection.concurrent.TrieMap[Module, Module]()

  override def apply(input: Module): Module = {
    memoization.getOrElseUpdate(input, f(Module(input.name, input.imports map this, input.localSentences, input.att)))
  }
}

object DefinitionTransformer {
  def from(f: java.util.function.UnaryOperator[Module]): DefinitionTransformer = DefinitionTransformer(f(_))
  def apply(f: Module => Module): DefinitionTransformer = new DefinitionTransformer(ModuleTransformer(f))
}

class DefinitionTransformer(moduleTransformer: ModuleTransformer) extends (Definition => Definition) {
  override def apply(d: Definition): Definition = {
    definition.Definition(
      moduleTransformer(d.mainModule),
      moduleTransformer(d.mainSyntaxModule),
      d.modules map moduleTransformer)
  }
}
