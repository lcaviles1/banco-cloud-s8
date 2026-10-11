package cl.bancoxyz.common;

import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.*;
@RestControllerAdvice
public class ApiErrors {
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(){return ResponseEntity.badRequest().body(Map.of("error","Solicitud invalida"));}
 @ExceptionHandler(org.springframework.web.client.HttpClientErrorException.class) ResponseEntity<?> remote(org.springframework.web.client.HttpClientErrorException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("error","Solicitud rechazada por el servicio"));}
 @ExceptionHandler(DuplicateKeyException.class) ResponseEntity<?> conflict(){return ResponseEntity.status(409).body(Map.of("error","Conflicto concurrente; reintente con la misma clave"));}
 @ExceptionHandler(org.springframework.web.client.ResourceAccessException.class) ResponseEntity<?> down(){return ResponseEntity.status(503).body(Map.of("error","Servicio temporalmente no disponible"));}
}
