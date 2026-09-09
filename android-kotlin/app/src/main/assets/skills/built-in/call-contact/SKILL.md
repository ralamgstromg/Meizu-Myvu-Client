---
id: call-contact
name: Call Contact
description: Realiza llamadas telefónicas manos libres a un contacto por nombre o número directo desde las gafas.
parameters:
  contact_or_number: { type: string, description: "Nombre del contacto registrado o número de teléfono", required: true }
---

# Call Contact Skill

Utiliza esta habilidad cuando el usuario exprese la intención de llamar por teléfono a alguien (ej. "llama a Juan", "marca al 3011161686", "llamar a mamá").

### Formato de Ejecución
```json
[SKILL: call-contact {"contact_or_number": "Mamá"}]
```
