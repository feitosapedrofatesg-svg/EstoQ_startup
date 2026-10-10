package com.estoq.business.notasFiscais;

import com.estoq.core.exceptions.FieldValidationException;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class RateioNota {
    /** O ajuste é a diferença para o total: desconto já aplicado não é descontado de novo. */
    public List<BigDecimal> calcular(DadosNota nota) {
        var liquidos = nota.itens().stream().map(i -> i.valorBruto().subtract(i.desconto())).toList();
        var soma = liquidos.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (soma.signum()==0 && nota.valorTotal().signum()!=0)
            throw new FieldValidationException("valorTotal", "Não é possível ratear despesas sem valor dos produtos.");
        var resultado = new ArrayList<BigDecimal>();
        BigDecimal distribuido = BigDecimal.ZERO;
        BigDecimal acumulado = BigDecimal.ZERO;
        for (int i=0; i<liquidos.size(); i++) {
            // A soma acumulada garante centavos exatos sem jogar todo o resíduo no último item.
            acumulado = acumulado.add(liquidos.get(i));
            var parcelaAcumulada = soma.signum()==0 ? BigDecimal.ZERO
                    : nota.valorTotal().multiply(acumulado).divide(soma,2,RoundingMode.HALF_UP);
            resultado.add(parcelaAcumulada.subtract(distribuido));
            distribuido = parcelaAcumulada;
        }
        return resultado;
    }
}
