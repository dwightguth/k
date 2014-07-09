// Copyright (c) 2012-2014 K Team. All Rights Reserved.
require "bool.k"

module LTL-SYNTAX-HOOKS
  syntax #LtlFormula ::= "TrueLtl" [function, hook(#LTL:true)]
  syntax #LtlFormula ::= "FalseLtl" [function, hook(#LTL:false)]

  syntax LtlFormula ::= #LtlFormula

endmodule

module LTL-HOOKS
  imports LTL-SYNTAX-HOOKS

  syntax LtlFormula ::= "~Ltl" LtlFormula [function, hook(#LTL:not),
                                           latex(\mathop{\sim_{\scriptstyle\it Ltl}}{#1})]
                     |  "OLtl" LtlFormula [function, hook(#LTL:next),
                                           latex(\mathop{\mathsf{O}_{\scriptstyle\it Ltl}}{#1})]
                     |  "<>Ltl" LtlFormula [function, hook(#LTL:eventually),
                                            latex(\mathop{\diamond_{\scriptstyle\it Ltl}}{#1})]
                     |  "[]Ltl" LtlFormula [function, hook(#LTL:always),
                                            latex(\mathop{\square_{\scriptstyle\it Ltl}}{#1})]
                     | "(" LtlFormula ")" [bracket]
                     >  LtlFormula "/\\Ltl" LtlFormula [function, left, hook(#LTL:and),
                                                        latex({#1}\mathrel{\land_{\scriptstyle\it Ltl}}{#2})]
                     >  LtlFormula "\\/Ltl" LtlFormula [function, left, hook(#LTL:or),
                                                        latex({#1}\mathrel{\lor_{\scriptstyle\it Ltl}}{#2})]
                     > left:
                        LtlFormula "ULtl" LtlFormula  [function, left, hook(#LTL:until),
                                                       latex({#1}\mathrel{\mathsf{U}_{\scriptstyle\it Ltl}}{#2})]
                     |  LtlFormula "RLtl" LtlFormula  [function, left, hook(#LTL:release),
                                                       latex({#1}\mathrel{\mathsf{R}_{\scriptstyle\it Ltl}}{#2})]
                     |  LtlFormula "WLtl" LtlFormula  [function, left, hook(#LTL:unless),
                                                       latex({#1}\mathrel{\mathsf{W}_{\scriptstyle\it Ltl}}{#2})]
                     |  LtlFormula "|->Ltl" LtlFormula [function, left, hook(#LTL:leadsTo),
                                                        latex({#1}\mathrel{\mapsto_{\scriptstyle\it Ltl}}{#2})]
                     > left:
                        LtlFormula "->Ltl" LtlFormula  [function, left, hook(#LTL:implies),
                                                        latex({#1}\mathrel{\right{arrow}_{\scriptstyle\it Ltl}}{#2})]
                     |  LtlFormula "<->Ltl" LtlFormula [function, left, hook(#LTL:iff),
                                                        latex({#1}\mathrel{\leftrightarrow_{\scriptstyle\it Ltl}}{#2})]
                     |  LtlFormula "=>Ltl" LtlFormula  [function, left, hook(#LTL:strongImplies),
                                                        latex({#1}\mathrel{\Rightarrow_{\scriptstyle\it Ltl}}{#2})]
                     |  LtlFormula "<=>Ltl" LtlFormula [function, left, hook(#LTL:strongIff),
                                                        latex({#1}\mathrel{\Leftightarrow_{\scriptstyle\it Ltl}}{#2})]

  rule A ->Ltl B => ~Ltl A \/Ltl B
  rule A <->Ltl B => (A ->Ltl B) /\Ltl (B ->Ltl A)
  rule <>Ltl A => TrueLtl ULtl A
  rule []Ltl A => FalseLtl RLtl A
  rule A WLtl B => (A ULtl B) \/Ltl []Ltl A
  rule A |->Ltl B => []Ltl (A ->Ltl (<>Ltl B))
  rule A =>Ltl B:LtlFormula => []Ltl (A ->Ltl B)
  rule A <=>Ltl B => []Ltl (A <->Ltl B)
endmodule

module MODEL-CHECKER-SYNTAX-HOOKS
  imports LTL-SYNTAX-HOOKS
  syntax #ModelCheckerState
  syntax ModelCheckerState ::= #ModelCheckerState 
  syntax #Prop
  syntax Prop ::= #Prop
  syntax #ModelCheckResult
  syntax ModelCheckResult ::= #ModelCheckResult
  syntax LtlFormula ::= Prop
endmodule


module  MODEL-CHECKER-INTERFACE

  syntax Bool ::= ModelCheckerState "|=Ltl" Prop [latex({#1}\mathrel{\models_{\scriptstyle\it Ltl}}{#2}), hook(#LTL:satisfies)]
  syntax Bool ::= Bag "|=Ltl" Prop [latex({#1}\mathrel{\models_{\scriptstyle\it Ltl}}{#2})]

endmodule

module MODEL-CHECKER-HOOKS
  imports MODEL-CHECKER-INTERFACE
  imports MODEL-CHECKER-SYNTAX-HOOKS
  imports BOOL-HOOKS

  syntax ModelCheckResult ::= "modelCheck" "(" ModelCheckerState "," LtlFormula ")" [hook(#LTL:modelCheck)]


endmodule
