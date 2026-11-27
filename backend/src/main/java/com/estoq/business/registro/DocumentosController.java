package com.estoq.business.registro;
import org.springframework.web.bind.annotation.*;
@RestController
public class DocumentosController {
    public record Versoes(String termos,String privacidade,String idioma,boolean termosEmRevisao) {}
    @GetMapping("/api/documentos") public Versoes versoes(){return new Versoes(DocumentosService.TERMOS,DocumentosService.PRIVACIDADE,"pt-BR",true);}
}
