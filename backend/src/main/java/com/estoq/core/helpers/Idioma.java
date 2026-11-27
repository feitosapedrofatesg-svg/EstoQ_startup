package com.estoq.core.helpers;

import java.util.Locale;
import java.util.Map;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

/** Tradução de apresentação. Não altera nomes, valores, moeda ou regras de negócio. */
public final class Idioma {
    private Idioma() {}
    private static final Map<String, Map<String,String>> CATALOGOS = Map.of("en", carregar("en"), "es", carregar("es"));

    private static Map<String,String> carregar(String idioma) {
        try(var entrada=Idioma.class.getResourceAsStream("/i18n/relatorios_"+idioma+".json")) {
            var mapper=new ObjectMapper();
            Map<String,String> textos=mapper.readValue(entrada,mapper.getTypeFactory().constructMapType(Map.class,String.class,String.class));
            return Map.copyOf(textos);
        } catch(Exception erro) {throw new IllegalStateException("Não foi possível carregar as traduções.",erro);}
    }
    public static String atual() {
        if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
            var request=atributos.getRequest();
            if(request.getHeader("Accept-Language")!=null) {
                var idioma=request.getLocale().getLanguage();
                if(idioma.equals("en")||idioma.equals("es"))return idioma;
            }
        }
        return "pt-BR";
    }
    public static Locale locale() {return Locale.forLanguageTag(atual());}
    public static String texto(String texto) {return texto(texto,atual());}
    public static String texto(String texto,String idioma) {
        if(texto==null)return null;
        return CATALOGOS.getOrDefault(idioma,Map.of()).getOrDefault(texto,texto);
    }
}
