package com.fastservices.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.*;
import java.util.List;

// IE3: Estructura de capas y Spring Boot
@SpringBootApplication
public class BackendCoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendCoreApplication.class, args);
    }
}

// --- MODELOS (IE8: Anotaciones JPA y relaciones) ---
@Entity
@Table(name = "clientes")
class Cliente {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
}

// --- REPOSITORIOS (IE7: Persistencia) ---
@Repository
interface ClienteRepository extends JpaRepository<Cliente, Long> {}

// --- SERVICIOS (IE18: Lógica y Simulación Faas/SQS) ---
@Service
class ClienteService {
    @Autowired private ClienteRepository repository;

    public Cliente crearCliente(Cliente cliente) {
        System.out.println("Enviando evento a cola AWS SQS para procesamiento Serverless...");
        return repository.save(cliente);
    }

    public List<Cliente> obtenerTodos() {
        return repository.findAll();
    }
}

// --- CONTROLADORES (IE4: Endpoints RESTful) ---
@RestController
@RequestMapping("/api/clientes")
class ClienteController {
    @Autowired private ClienteService service;

    @PostMapping
    public Cliente registrar(@RequestBody Cliente cliente) {
        return service.crearCliente(cliente);
    }

    @GetMapping
    public List<Cliente> listar() {
        return service.obtenerTodos();
    }
}