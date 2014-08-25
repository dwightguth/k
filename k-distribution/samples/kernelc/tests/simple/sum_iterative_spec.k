// Copyright (c) 2014 K Team. All Rights Reserved.
require "../../kernelc.k"

module SUM-ITERATIVE-SPEC
  imports KERNELC

rule
  <k>
    while (n > 0) {
      s = s + n;
      n = n - 1;
    }
  =>
    .K
  ...</k>
  <env>...
    n |-> tv(int *, object(P1:Int))
    s |-> tv(int *, object(P2:Int))
  ...</env>
  <heap>...
    object(P1) |-> tv(int, N:MInt{bitwidth(32)} => mi(32, 0))
    object(P2) |-> tv(int, S:MInt{bitwidth(32)} => addMInt(S, udivMInt(mulMInt(N, addMInt(N, mi(32, 1))), mi(32, 2))))
  ...</heap>
requires sgeMInt(N, mi(32, 0)) andBool sltMInt(N, mi(32, 65536))

rule
  <fun>... FUN:Map ...</fun>
  <k>
    sum_iterative(tv(int, N:MInt{bitwidth(32)}))
  =>
    tv(int, udivMInt(mulMInt(N, addMInt(N, mi(32, 1))), mi(32, 2)))
  ...</k> 
requires sgeMInt(N, mi(32, 0)) andBool sltMInt(N, mi(32, 65536))

endmodule
