# Portal HTTPS do cliente

Este módulo implementa a página compacta definida para o cliente:

- BR visível;
- Em mãos;
- Vizinho (nome + contato);
- Portaria;
- Varanda/Caixa de correio;
- Ninguém para receber;
- Tem palavra-chave? SIM/NÃO; se SIM, aparece "Qual?";
- mapa com carro laranja e caixa laranja;
- botão muda para "✓ PEDIDO CONFIRMADO" sem trocar de página;
- layout `100dvh`, sem rolagem vertical.

## Executar localmente

```bash
python -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/uvicorn main:app --host 0.0.0.0 --port 8080
```

No Windows, ative a venv conforme o PowerShell.

Para virar um link HTTPS real é necessário publicar este serviço em um host HTTPS. O APK não inventa uma URL pública sozinho.

## Importante

O envio silencioso de WhatsApp não está implementado aqui. Para o RouteCopilot mostrar estados reais "enviada/entregue/lida" sem abrir o WhatsApp, deve-se integrar a API oficial do WhatsApp Business no backend.
