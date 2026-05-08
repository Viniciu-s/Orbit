# Orbit — Event Bus for Modern Java Applications

## Visão Geral

**Orbit** é uma biblioteca Java de comunicação orientada a eventos (*Event Bus*) criada para simplificar a troca de informações entre diferentes partes de uma aplicação de forma desacoplada, moderna e performática.

A proposta da Orbit é oferecer uma API simples e elegante para publicação e consumo de eventos, permitindo que módulos do sistema se comuniquem sem depender diretamente uns dos outros.

---

## O Problema que a Orbit Resolve

Em aplicações modernas, principalmente:

- ERPs
- Microsserviços
- Sistemas modulares
- Aplicações concorrentes
- Arquiteturas orientadas a eventos

é comum que diferentes partes do sistema precisem reagir a ações específicas. Exemplos de eventos:

- Usuário criado
- Pagamento aprovado
- Pedido finalizado
- Estoque atualizado
- Notificação enviada

Sem um Event Bus, os módulos acabam ficando altamente acoplados:

```
UserService → EmailService
UserService → NotificationService
UserService → AuditService
```

Isso torna:

- Manutenção difícil
- Escalabilidade complicada
- Testes mais complexos
- Arquitetura rígida

---

## O Papel da Orbit

A Orbit atua como uma camada intermediária de comunicação entre componentes do sistema.

> Em vez de um módulo chamar diretamente outro módulo, ele apenas **publica um evento**.  
> Os módulos interessados **escutam** esse evento e reagem automaticamente.

### Exemplo Conceitual

**Sem Orbit** — forte acoplamento:

```
UserService
├── EmailService
├── AuditService
├── NotificationService
└── AnalyticsService
```

**Com Orbit** — arquitetura desacoplada:

```
UserService
    ↓
Orbit Event Bus
    ↓
├── EmailListener
├── AuditListener
├── NotificationListener
└── AnalyticsListener
```

---

## Objetivos da Biblioteca

| Objetivo | Descrição |
|---|---|
| **Simplicidade** | API intuitiva e fácil de usar |
| **Performance** | Baixo overhead e alta velocidade de publicação de eventos |
| **Desacoplamento** | Separar responsabilidades entre módulos |
| **Concorrência** | Suporte a execução assíncrona e ambientes multithread |
| **Escalabilidade** | Facilitar crescimento arquitetural da aplicação |
| **Developer Experience** | Boa documentação, API fluente e mensagens claras |

---

## Principais Funcionalidades

### Publicação de Eventos

Permite emitir eventos para toda a aplicação.

```java
orbit.publish(new UserCreatedEvent());
```

### Registro de Listeners

Permite registrar ouvintes para eventos específicos.

```java
orbit.subscribe(UserCreatedEvent.class, event -> {
    System.out.println(event.username());
});
```

### Execução Assíncrona

Suporte a processamento paralelo utilizando threads.

```java
orbit.publishAsync(event);
```

### Prioridade de Eventos

Listeners podem possuir prioridades diferentes.

### Eventos Únicos

Listeners que executam apenas uma vez.

### Sistema Baseado em Annotations

Registro automático de métodos anotados.

```java
@Subscribe
public void onUserCreated(UserCreatedEvent event) {}
```

### Thread Safety

Estruturas seguras para ambientes concorrentes.

### Error Handling

Tratamento isolado de falhas em listeners. Um listener com erro não interrompe os demais.

---

## Casos de Uso

A Orbit pode ser utilizada em:

- **Sistemas ERP** — atualização de estoque, geração de auditoria, notificações, integração entre módulos
- **Aplicações Web** — autenticação, notificações, analytics, eventos de domínio
- **Jogos** — eventos de jogador, física, sistemas de missão
- **Microsserviços** — comunicação interna, eventos de domínio, pipelines de processamento
- **Aplicações Desktop** — UI events, plugins, módulos desacoplados

---

## Filosofia da Orbit

A Orbit segue uma filosofia de design baseada em:

- **Minimalismo** — poucas dependências externas
- **API Fluente** — código limpo e legível
- **Modularidade** — cada funcionalidade pode evoluir independentemente
- **Extensibilidade** — suporte a interceptors, pipelines, plugins, métricas, canais e middlewares

```java
Orbit.create()
    .subscribe(UserEvent.class, listener)
    .publish(event);
```

---

## Arquitetura Geral

A arquitetura da Orbit é baseada em quatro componentes principais:

| Componente | Responsabilidade |
|---|---|
| **Event** | Representa uma ocorrência dentro do sistema |
| **EventListener** | Responsável por reagir ao evento |
| **EventBus** | Registra listeners, distribui eventos e controla execução |
| **Dispatcher** | Camada interna responsável pelo roteamento dos eventos |

---

## Benefícios de Utilizar Orbit

- Código mais limpo
- Menor acoplamento
- Melhor manutenção
- Arquitetura moderna
- Melhor escalabilidade
- Facilidade de testes
- Separação de responsabilidades

---

## Exemplo Completo

```java
Orbit orbit = Orbit.create();

orbit.subscribe(UserCreatedEvent.class, event -> {
    System.out.println("Novo usuário: " + event.name());
});

orbit.publish(new UserCreatedEvent("Vinicius"));
```

---

## Roadmap

| Versão | Funcionalidades |
|---|---|
| **v1.0** | `publish`, `subscribe`, `unsubscribe`, suporte assíncrono |
| **v1.1** | Prioridades, listeners únicos, tratamento de erros |
| **v1.2** | Annotations, scanner por reflexão |
| **v2.0** | Canais, interceptors, métricas, eventos distribuídos |

---

## Público-Alvo

Orbit foi criada para:

- Desenvolvedores Java
- Aplicações backend
- Arquiteturas modulares
- Sistemas concorrentes
- Projetos que precisam de desacoplamento

---

## Conclusão

**Orbit** é uma biblioteca Java moderna de Event Bus focada em simplicidade, desacoplamento e performance.
