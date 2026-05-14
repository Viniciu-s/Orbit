#!/bin/bash
# =============================================================================
# Orbit Release Helper Script
# 
# Automatiza o processo de criação de releases do Orbit Event Bus
# Uso: ./scripts/release.sh [version] [type]
#   version: Nova versão (ex: 1.0.0)
#   type: patch|minor|major (opcional, auto-incrementa a partir da versão atual)
# =============================================================================

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper functions
info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

success() {
    echo -e "${GREEN}✓${NC} $1"
}

warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

error() {
    echo -e "${RED}✗${NC} $1"
    exit 1
}

# Check if we're in the project root
if [ ! -f "pom.xml" ]; then
    error "Este script deve ser executado na raiz do projeto Orbit"
fi

# Get current version from pom.xml
CURRENT_VERSION=$(grep -oP '<version>\K[^<]+' pom.xml | head -1)
info "Versão atual: $CURRENT_VERSION"

# Parse version argument or calculate next version
if [ -z "$1" ]; then
    error "Uso: ./scripts/release.sh [version] ou ./scripts/release.sh [patch|minor|major]"
fi

# Check if first argument is a version bump type
if [[ "$1" =~ ^(patch|minor|major)$ ]]; then
    # Auto-increment version
    IFS='.' read -ra VER <<< "${CURRENT_VERSION%-SNAPSHOT}"
    MAJOR="${VER[0]}"
    MINOR="${VER[1]}"
    PATCH="${VER[2]}"
    
    case "$1" in
        patch)
            PATCH=$((PATCH + 1))
            ;;
        minor)
            MINOR=$((MINOR + 1))
            PATCH=0
            ;;
        major)
            MAJOR=$((MAJOR + 1))
            MINOR=0
            PATCH=0
            ;;
    esac
    
    NEW_VERSION="$MAJOR.$MINOR.$PATCH"
    info "Auto-incrementando versão ($1): $CURRENT_VERSION → $NEW_VERSION"
else
    NEW_VERSION="$1"
    info "Nova versão: $NEW_VERSION"
fi

# Validate version format
if ! [[ "$NEW_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    error "Formato de versão inválido. Use: X.Y.Z (ex: 1.0.0)"
fi

# Check if version already exists as a tag
if git rev-parse "v$NEW_VERSION" >/dev/null 2>&1; then
    error "Tag v$NEW_VERSION já existe! Use outra versão."
fi

# Confirm with user
echo ""
warning "Você está prestes a criar uma release:"
echo "  • Versão: $NEW_VERSION"
echo "  • Tag: v$NEW_VERSION"
echo "  • Branch: $(git branch --show-current)"
echo ""
read -p "Continuar? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    info "Release cancelado."
    exit 0
fi

# Step 1: Run tests
echo ""
info "🧪 Executando testes..."
./mvnw clean verify || error "Testes falharam! Corrija antes de continuar."
success "Todos os testes passaram"

# Step 2: Update version in pom.xml
echo ""
info "📝 Atualizando versão no pom.xml..."
sed -i "0,/<version>.*<\/version>/s/<version>.*<\/version>/<version>$NEW_VERSION<\/version>/" pom.xml
success "pom.xml atualizado: $CURRENT_VERSION → $NEW_VERSION"

# Step 3: Check if CHANGELOG.md needs update
echo ""
if ! grep -q "\[$NEW_VERSION\]" CHANGELOG.md; then
    warning "CHANGELOG.md não contém entrada para versão $NEW_VERSION"
    warning "Por favor, adicione as mudanças antes de continuar:"
    echo ""
    echo "## [$NEW_VERSION] - $(date +%Y-%m-%d)"
    echo ""
    echo "### Added"
    echo "- Nova feature"
    echo ""
    echo "### Changed"
    echo "- Mudança"
    echo ""
    echo "### Fixed"
    echo "- Correção"
    echo ""
    read -p "Pressione Enter após atualizar CHANGELOG.md..." -r
fi

# Step 4: Commit changes
echo ""
info "💾 Criando commit..."
git add pom.xml CHANGELOG.md
git commit -m "chore: Bump version to $NEW_VERSION" || warning "Nada para commitar"
success "Commit criado"

# Step 5: Create annotated tag
echo ""
info "🏷️  Criando tag v$NEW_VERSION..."
git tag -a "v$NEW_VERSION" -m "Release version $NEW_VERSION"
success "Tag criada: v$NEW_VERSION"

# Step 6: Show next steps
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
success "Release preparado com sucesso!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
info "Próximos passos:"
echo ""
echo "1. Revisar as mudanças:"
echo "   ${YELLOW}git show HEAD${NC}"
echo ""
echo "2. Push do commit e tag (dispara release automático):"
echo "   ${GREEN}git push origin $(git branch --show-current)${NC}"
echo "   ${GREEN}git push origin v$NEW_VERSION${NC}"
echo ""
echo "3. Acompanhar o workflow de release:"
echo "   ${BLUE}https://github.com/Viniciu-s/Orbit/actions${NC}"
echo ""
echo "4. Verificar publicação no Maven Central (~30 min):"
echo "   ${BLUE}https://search.maven.org/artifact/io.github.viniciu-s/orbit/$NEW_VERSION/jar${NC}"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
warning "Se algo der errado antes do push, você pode reverter com:"
echo "   ${YELLOW}git reset --hard HEAD~1${NC}"
echo "   ${YELLOW}git tag -d v$NEW_VERSION${NC}"
echo ""
