// Copyright (c) 2012-2014 K Team. All Rights Reserved.
require "k-equal.k"
module INT-SYNTAX-HOOKS
  syntax #Int ::= Token{[\-\+]? [0-9]+} [regex("[\\+-]?\\d+"), prefer]
  syntax Int ::= #Int
endmodule

module INT-HOOKS
  imports INT-SYNTAX-HOOKS
  imports BOOL-HOOKS
  imports K-EQUAL-HOOKS

  syntax Int ::= "~Int" Int                     [function, latex(\mathop{\sim_{\scriptstyle\it Int}}{#1}), hook(#INT:not)]
               > left:
                 Int "^Int" Int                 [function, left, latex({#1}\mathrel{{\char`\^}_{\!\scriptstyle\it Int}}{#2}), hook(#INT:pow)]
               > left:
                 Int "*Int" Int                 [function, left, smtlib(*), latex({#1}\mathrel{\ast_{\scriptstyle\it Int}}{#2}), hook(#INT:mul)]
               /* FIXME: translate /Int and %Int into smtlib */
               /* /Int and %Int implement t-division */
               | Int "/Int" Int                 [function, left, latex({#1}\mathrel{\div_{\scriptstyle\it Int}}{#2}), hook(#INT:tdiv)]
               | Int "%Int" Int                 [function, left, latex({#1}\mathrel{\%_{\scriptstyle\it Int}}{#2}), hook(#INT:trem)]
               /* divInt and modInt implement e-division */
               | Int "divInt" Int               [function, left, smtlib(div), hook(#INT:ediv)]
               | Int "modInt" Int               [function, left, smtlib(mod), hook(#INT:erem)]
               > left:
                 Int "+Int" Int                 [function, left, smtlib(+), latex({#1}\mathrel{+_{\scriptstyle\it Int}}{#2}), hook(#INT:add), cons(Int1PlusSyn)]
               | Int "-Int" Int                 [function, left, smtlib(-), latex({#1}\mathrel{-_{\scriptstyle\it Int}}{#2}), hook(#INT:sub)]
               > left:
                 Int ">>Int" Int                [function, left, latex({#1}\mathrel{\gg_{\scriptstyle\it Int}}{#2}), hook(#INT:shr)]
               | Int "<<Int" Int                [function, left, latex({#1}\mathrel{\ll_{\scriptstyle\it Int}}{#2}), hook(#INT:shl)]
               > left:
                 Int "&Int" Int                 [function, left, latex({#1}\mathrel{\&_{\scriptstyle\it Int}}{#2}), hook(#INT:and)]
               > left:
                 Int "xorInt" Int               [function, left, latex({#1}\mathrel{\oplus_{\scriptstyle\it Int}}{#2}), hook(#INT:xor)]
               > left:
                 Int "|Int" Int                 [function, left, latex({#1}\mathrel{|_{\scriptstyle\it Int}}{#2}), hook(#INT:or)]
               > "minInt" "(" Int "," Int ")"   [function]
               | "maxInt" "(" Int "," Int ")"   [function]
               | "absInt" "(" Int ")"           [function, smtlib(abs), klabel(absInt), hook(#INT:abs)]

  rule I1:#Int divInt I2:#Int => (I1 -Int (I1 modInt I2)) /Int I2
  when I2 =/=Int 0
  rule
    I1:#Int modInt I2:#Int
  =>
    ((I1 %Int absInt(I2)) +Int absInt(I2)) %Int absInt(I2)
  when I2 =/=Int 0

  rule minInt(I1:Int, I2:Int) => #if I1 <Int I2 #then I1 #else I2 #fi
  rule maxInt(I1:Int, I2:Int) => #if I1 >Int I2 #then I1 #else I2 #fi

  // absInt hooked
  // rule absInt(I:Int) => I when I >=Int 0
  // rule absInt(I:Int) => 0 -Int I when I <Int 0

  syntax Bool ::= Int "<=Int" Int         [function, left, smtlib(<=), latex({#1}\mathrel{\leq_{\scriptstyle\it Int}}{#2}), hook(#INT:le)]
                | Int "<Int" Int          [function, left, smtlib(<), latex({#1}\mathrel{<_{\scriptstyle\it Int}}{#2}), hook(#INT:lt)]
                | Int ">=Int" Int         [function, left, smtlib(>=), latex({#1}\mathrel{\geq_{\scriptstyle\it Int}}{#2}), hook(#INT:ge)]
                | Int ">Int" Int          [function, left, smtlib(>), latex({#1}\mathrel{>_{\scriptstyle\it Int}}{#2}), hook(#INT:gt)]
                | Int "==Int" Int         [function, left, smtlib(=), latex({#1}\mathrel{{=}{=}_{\scriptstyle\it Int}}{#2}), hook(#INT:eq)]
                | Int "=/=Int" Int        [function, left, smtlib(distinct), latex({#1}\mathrel{{=}{/}{=}_{\scriptstyle\it Int}}{#2}), hook(#INT:ne)]
                | Int "=Int" Int          [equality, function, hook(#INT:eq)]
                | Int "dividesInt" Int    [function]

  rule I1:Int =/=Int I2:Int => notBool(I1 ==Int I2)
  rule (I1:Int dividesInt I2:Int) => (I2 %Int I1) ==Int 0

  //sort Nat ::= {I:Int | I >=Int 0}
  syntax Nat
  rule isNat(I:Int) => I >=Int 0 [function, anywhere]

  rule 0 +Int I:Int => I [anywhere]
  rule 0 *Int I:Int => 0 [anywhere]

  syntax Int ::= freshInt(Int)    [freshGenerator, function]
  rule freshInt(I:Int) => I
endmodule

