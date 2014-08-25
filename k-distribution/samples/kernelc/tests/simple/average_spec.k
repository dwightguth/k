// Copyright (c) 2014 K Team. All Rights Reserved.
require "../../kernelc.k"

module AVERAGE-SPEC
  imports KERNELC

rule
  <fun>... FUN:Map ...</fun>
  <k>
    average(tv(int, X:MInt{bitwidth(32)}), tv(int, Y:MInt{bitwidth(32)}), tv(int, Z:MInt{bitwidth(32)}))
  =>
    tv(int, miMInt(sdivMInt(addMInt(addMInt(X, Y), Z), mi(32, 3))))
  ...</k> 

endmodule
