---
id: call-contact
name: Call Contact
description: Permite realizar o iniciar una llamada telefónica a un contacto por nombre o número.
parameters:
  contact_or_number: { type: string, description: "Nombre del contacto registrado o número de teléfono", required: true }
---

# Call Contact Skill

Utiliza esta habilidad cuando el usuario exprese la intención de llamar por teléfono a alguien (ej. "llama a Juan", "marca al 5551234").

### Formato de Ejecución
```json
[SKILL: call-contact {"contact_or_number": "Juan"}]
```
