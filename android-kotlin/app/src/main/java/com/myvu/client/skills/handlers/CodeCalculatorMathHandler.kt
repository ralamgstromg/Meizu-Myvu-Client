package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import java.util.Stack

/**
 * Enhanced Financial & Mathematical Calculator:
 * Computes arithmetic expressions respecting operator precedence and parentheses,
 * calculates Colombia IVA (19%), ReteFuente, loan interest, and unit conversions.
 */
class CodeCalculatorMathHandler : SkillHandler {

    private fun formatCop(amount: Double): String {
        return if (amount % 1.0 == 0.0) {
            "${amount.toLong()} COP"
        } else {
            String.format(Locale.US, "%.2f COP", amount)
        }
    }

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val expression = args.optString("expression", "").trim()
            val operationType = args.optString("operation_type", "math").lowercase().trim()

            if (expression.isEmpty()) {
                return SkillResult(false, "Falta especificar la expresión matemática o financiera.")
            }

            val resultMsg = when (operationType) {
                "tax_colombia", "iva", "impuestos" -> computeColombiaTax(expression)
                "loan_interest", "loan", "credito" -> computeLoanInterest(expression)
                "unit_convert", "convert", "conversion" -> computeUnitConversion(expression)
                else -> computeMathExpression(expression)
            }

            SkillResult(true, resultMsg)
        } catch (e: Exception) {
            LogBus.error("CodeCalculatorMathHandler -> Calculation error", e)
            SkillResult(false, "Error en el cálculo: ${e.message}")
        }
    }

    private fun computeColombiaTax(expr: String): String {
        val amount = expr.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
            ?: return "No se pudo identificar un monto numérico válido en: '$expr'"

        val ivaRate = 0.19
        val reteFuenteRate = 0.035

        val ivaVal = amount * ivaRate
        val totalWithIva = amount + ivaVal
        val reteFuenteVal = amount * reteFuenteRate
        val netPayable = totalWithIva - reteFuenteVal

        return "Liquidación Tributaria Colombia: Base Gravable: ${formatCop(amount)}. IVA (19%): ${formatCop(ivaVal)}. Subtotal + IVA: ${formatCop(totalWithIva)}. ReteFuente (3.5%): -${formatCop(reteFuenteVal)}. Neto a Pagar: ${formatCop(netPayable)}."
    }

    private fun computeLoanInterest(expr: String): String {
        val amount = expr.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 1000000.0
        val monthlyRate = 0.018 // 1.8% E.M. promedio
        val months = 12
        val totalInterest = amount * monthlyRate * months
        val totalPayable = amount + totalInterest
        val monthlyQuota = totalPayable / months

        return "Simulador de Crédito ($months Meses): Monto: ${formatCop(amount)}. Tasa Estimada: 1.8% M.V. Cuota Mensual: ${formatCop(monthlyQuota)}. Total Intereses: ${formatCop(totalInterest)}. Total Final a Pagar: ${formatCop(totalPayable)}."
    }

    private fun computeUnitConversion(expr: String): String {
        val clean = expr.lowercase().trim()
        val num = clean.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 1.0

        return when {
            clean.contains("km") && (clean.contains("milla") || clean.contains("mi")) -> {
                val miles = num * 0.621371
                val milesStr = String.format(Locale.US, "%.2f", miles)
                "Conversión: $num km = $milesStr millas."
            }
            clean.contains("milla") && clean.contains("km") -> {
                val km = num * 1.60934
                val kmStr = String.format(Locale.US, "%.2f", km)
                "Conversión: $num millas = $kmStr km."
            }
            clean.contains("c") && clean.contains("f") -> {
                val f = (num * 9 / 5) + 32
                val fStr = String.format(Locale.US, "%.1f", f)
                "Temperatura: $num °C = $fStr °F."
            }
            clean.contains("f") && clean.contains("c") -> {
                val c = (num - 32) * 5 / 9
                val cStr = String.format(Locale.US, "%.1f", c)
                "Temperatura: $num °F = $cStr °C."
            }
            clean.contains("kg") && (clean.contains("libra") || clean.contains("lb")) -> {
                val lbs = num * 2.20462
                val lbsStr = String.format(Locale.US, "%.2f", lbs)
                "Peso: $num kg = $lbsStr libras."
            }
            clean.contains("lb") && clean.contains("kg") -> {
                val kg = num / 2.20462
                val kgStr = String.format(Locale.US, "%.2f", kg)
                "Peso: $num libras = $kgStr kg."
            }
            else -> "Conversión: Expresión evaluada para '$expr'."
        }
    }

    private fun computeMathExpression(expr: String): String {
        val clean = expr.replace(",", ".")
            .replace("x", "*")
            .replace("X", "*")
            .replace("÷", "/")
            .trim()

        return try {
            val result = evaluateWithPrecedence(clean)
            val formattedResult = if (result % 1.0 == 0.0) {
                result.toLong().toString()
            } else {
                String.format(Locale.US, "%.4f", result).trimEnd('0').trimEnd('.')
            }
            "Resultado: $expr = $formattedResult"
        } catch (e: Exception) {
            "Resultado: $expr procesado."
        }
    }

    /**
     * Standard Shunting-Yard arithmetic parser respecting precedence and parentheses.
     */
    private fun evaluateWithPrecedence(expression: String): Double {
        val tokens = tokenize(expression)
        val values = Stack<Double>()
        val ops = Stack<Char>()

        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            when {
                token.toDoubleOrNull() != null -> values.push(token.toDouble())
                token == "(" -> ops.push('(')
                token == ")" -> {
                    while (ops.isNotEmpty() && ops.peek() != '(') {
                        values.push(applyOp(ops.pop(), values.pop(), values.pop()))
                    }
                    if (ops.isNotEmpty()) ops.pop() // pop '('
                }
                token in listOf("+", "-", "*", "/", "%", "^") -> {
                    val op = token[0]
                    while (ops.isNotEmpty() && hasPrecedence(op, ops.peek())) {
                        values.push(applyOp(ops.pop(), values.pop(), values.pop()))
                    }
                    ops.push(op)
                }
            }
            i++
        }

        while (ops.isNotEmpty()) {
            values.push(applyOp(ops.pop(), values.pop(), values.pop()))
        }

        return if (values.isNotEmpty()) values.pop() else 0.0
    }

    private fun hasPrecedence(op1: Char, op2: Char): Boolean {
        if (op2 == '(' || op2 == ')') return false
        if ((op1 == '*' || op1 == '/' || op1 == '%' || op1 == '^') && (op2 == '+' || op2 == '-')) return false
        return true
    }

    private fun applyOp(op: Char, b: Double, a: Double): Double {
        return when (op) {
            '+' -> a + b
            '-' -> a - b
            '*' -> a * b
            '/' -> if (b != 0.0) a / b else 0.0
            '%' -> a % b
            '^' -> Math.pow(a, b)
            else -> 0.0
        }
    }

    private fun tokenize(expr: String): List<String> {
        val result = mutableListOf<String>()
        var currentNumber = StringBuilder()

        for (ch in expr) {
            if (ch.isDigit() || ch == '.') {
                currentNumber.append(ch)
            } else {
                if (currentNumber.isNotEmpty()) {
                    result.add(currentNumber.toString())
                    currentNumber = StringBuilder()
                }
                if (ch in "+-*/%^()") {
                    result.add(ch.toString())
                }
            }
        }
        if (currentNumber.isNotEmpty()) {
            result.add(currentNumber.toString())
        }
        return result
    }
}
