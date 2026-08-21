---
id: code-calculator-math
name: Calculadora Financiera y Matemática
description: Realiza cálculos matemáticos de alta precisión, impuestos de Colombia (IVA 19%, ReteFuente), interés de préstamos y conversión de unidades.
parameters:
  expression:
    type: string
    description: Expresión matemática o valor numérico a evaluar (ej. "1500000 * 0.19", "500 USD a COP").
    required: true
  operation_type:
    type: string
    description: Tipo de operación (math, tax_colombia, loan_interest, unit_convert).
    required: false
---
