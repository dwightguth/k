// Copyright (c) 2014 K Team. All Rights Reserved.
require "../../kernelc.k"

module SUM-RECURSIVE-SPEC
  imports KERNELC

rule
  <fun>... FUN:Map ...</fun>
  <k>
    sum_recursive(tv(int, N:MInt{bitwidth(32)}))
  =>
    tv(int, udivMInt(mulMInt(N, addMInt(N, mi(32, 1))), mi(32, 2)))
  ...</k>
requires sgeMInt(N, mi(32, 0)) andBool sltMInt(N, mi(32, 65536))

endmodule
