package com.fastservices.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.ArrayList;

// SDK de AWS v2 para SQS
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

// Importación de Jackson compatible con Spring Boot 3 para evitar bucles infinitos en JSON
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Clase Principal de Arranque - Spring Boot 3 (EFT IE3 / EP2 IE2)
 */
@SpringBootApplication
public class BackendCoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendCoreApplication.class, args);
    }
}

// =========================================================================
// CONFIGURACIÓN AWS SQS (EFT IE18 / EP3 IE10)
// =========================================================================
@Configuration
class AwsConfig {
    @Value("${aws.region}") private String region;
    @Value("${aws.accessKeyId}") private String accessKey;
    @Value("${aws.secretAccessKey}") private String secretKey;

    @Bean
    public SqsClient sqsClient() {
        return SqsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                ))
                .build();
    }
}

// =========================================================================
// MODELOS / ENTIDADES JPA (EP2 IE6 / EFT IE8 - Relaciones OneToMany / ManyToOne)
// =========================================================================
@Entity
@Table(name = "clientes")
class Cliente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre no puede estar vacío")
    private String nombre;

    @Email(message = "Email inválido")
    @Column(unique = true)
    private String email;

    // Relación Uno a Muchos con Ordenes de Compra
    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore // Evita serialización recursiva e infinitos bucles en la API JSON
    private List<OrdenCompra> ordenes = new ArrayList<>();

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public List<OrdenCompra> getOrdenes() { return ordenes; }
    public void setOrdenes(List<OrdenCompra> ordenes) { this.ordenes = ordenes; }
}

@Entity
@Table(name = "ordenes_compra")
class OrdenCompra {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El producto no puede estar vacío")
    private String producto;

    @NotNull(message = "El total es requerido")
    @Positive(message = "El total debe ser un número positivo")
    private Double total;

    // Relación Muchos a Uno con Clientes (EFT IE8 / EP2 IE6)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProducto() { return producto; }
    public void setProducto(String producto) { this.producto = producto; }
    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }
    public Cliente getCliente() { return cliente; }
}

// DTO para el Request del Endpoint de Órdenes
class OrdenRequest {
    @NotNull(message = "Cliente ID es obligatorio")
    private Long clienteId;
    @NotBlank(message = "El producto es obligatorio")
    private String producto;
    @NotNull(message = "El monto total es obligatorio")
    private Double total;

    public Long getClienteId() { return clienteId; }
    public void setClienteId(Long clienteId) { this.clienteId = clienteId; }
    public String getProducto() { return producto; }
    public void setProducto(String producto) { this.producto = producto; }
    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }
}

// =========================================================================
// REPOSITORIOS (EP2 IE2 / EFT IE7)
// =========================================================================
@Repository
interface ClienteRepository extends JpaRepository<Cliente, Long> {}

@Repository
interface OrdenCompraRepository extends JpaRepository<OrdenCompra, Long> {}

// =========================================================================
// CAPA DE SERVICIO (EP2 IE2 / EFT IE3 - Lógica de Negocio y Encolamiento SQS)
// =========================================================================
@Service
class OrdenService {
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private OrdenCompraRepository ordenRepository;
    @Autowired private SqsClient sqsClient;

    @Value("${aws.sqs.queue.url}")
    private String queueUrl;

    @Transactional
    public OrdenCompra registrarOrdenYEnviarSQS(OrdenRequest request) {
        // 1. Validar la existencia del cliente en la BD relacional
        Cliente cliente = clienteRepository.findById(request.getClienteId())
                .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado con ID: " + request.getClienteId()));

        // 2. Persistir la orden de compra en PostgreSQL
        OrdenCompra orden = new OrdenCompra();
        orden.setProducto(request.getProducto());
        orden.setTotal(request.getTotal());
        orden.setCliente(cliente);
        OrdenCompra ordenGuardada = ordenRepository.save(orden);

        // 3. Generar payload JSON para la cola asíncrona de AWS SQS
        String jsonPayload = String.format(
            "{\"ordenId\": %d, \"clienteId\": %d, \"email\": \"%s\", \"producto\": \"%s\", \"total\": %.2f}",
            ordenGuardada.getId(), cliente.getId(), cliente.getEmail(), ordenGuardada.getProducto(), ordenGuardada.getTotal()
        );

        // 4. Envío asíncrono del mensaje a AWS SQS (EFT IE18 / EP3 IE14)
        try {
            SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(jsonPayload)
                    .build();
            sqsClient.sendMessage(sendMsgRequest);
            System.out.println("Mensaje encolado exitosamente en AWS SQS: " + jsonPayload);
        } catch (Exception e) {
            // Se registra el fallo pero no se interrumpe la transacción de la BD local
            System.err.println("Advertencia SQS (Simulado en local): " + e.getMessage());
        }

        return ordenGuardada;
    }
}

// =========================================================================
// CAPA CONTROLADOR (EP2 IE1 / EFT IE4 - RESTful CRUD Completo y Manejo Errores)
// =========================================================================
@RestController
@RequestMapping("/api/v1")
class CoreController {

    @Autowired private ClienteRepository clienteRepository;
    @Autowired private OrdenCompraRepository ordenCompraRepository;
    @Autowired private OrdenService ordenService;

    // --- CRUD DE CLIENTES (EP2 IE5 / EFT IE7) ---

    // 1. Crear Cliente (POST)
    @PostMapping("/clientes")
    public ResponseEntity<Cliente> crearCliente(@Valid @RequestBody Cliente cliente) {
        Cliente nuevoCliente = clienteRepository.save(cliente);
        return new ResponseEntity<>(nuevoCliente, HttpStatus.CREATED);
    }

    // 2. Listar Clientes (GET)
    @GetMapping("/clientes")
    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    // 3. Obtener un Cliente por ID (GET)
    @GetMapping("/clientes/{id}")
    public ResponseEntity<Cliente> obtenerCliente(@PathVariable Long id) {
        return clienteRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 4. Actualizar Cliente (PUT)
    @PutMapping("/clientes/{id}")
    public ResponseEntity<Cliente> actualizarCliente(@PathVariable Long id, @Valid @RequestBody Cliente clienteDetalles) {
        return clienteRepository.findById(id)
            .map(cliente -> {
                cliente.setNombre(clienteDetalles.getNombre());
                cliente.setEmail(clienteDetalles.getEmail());
                return ResponseEntity.ok(clienteRepository.save(cliente));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // 5. Eliminar Cliente (DELETE)
    @DeleteMapping("/clientes/{id}")
    public ResponseEntity<?> eliminarCliente(@PathVariable Long id) {
        return clienteRepository.findById(id)
            .map(cliente -> {
                clienteRepository.delete(cliente);
                return ResponseEntity.ok().build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // --- ENDPOINTS DE ÓRDENES ---

    // Crear Orden (POST) -> Guarda en Base de Datos y publica en la cola AWS SQS
    @PostMapping("/ordenes")
    public ResponseEntity<?> registrarOrden(@Valid @RequestBody OrdenRequest request) {
        try {
            OrdenCompra orden = ordenService.registrarOrdenYEnviarSQS(request);
            return new ResponseEntity<>(orden, HttpStatus.CREATED);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("{\"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"error\": \"Error procesando la transacción.\"}");
        }
    }

    // Listar Ordenes (GET)
    @GetMapping("/ordenes")
    public List<OrdenCompra> listarOrdenes() {
        return ordenCompraRepository.findAll();
    }
}